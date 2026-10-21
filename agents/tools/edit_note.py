"""让 agent 直接按块编辑笔记正文：先 read_note_blocks 拿到每块的 id 和内容指纹 hash，再用
edit_note 提交一批操作（insert/replace/delete/move），内容用 Markdown 写。改动直接生效，
每一轮对话的改动可在回复下方一键撤销（Java 侧在第一次改动前自动存快照）。
hash 用来发现"读取之后用户又改过这块"，不一致时 Java 整批拒绝，agent 应重新读取再改。
详见 docs/superpowers/specs/2026-10-11-agent-note-editing-design.md。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.java_client import JavaClient
from app.state import AgentState
from tools.content_cache import ContentCache

OFFLOAD_THRESHOLD_CHARS = 16000
SUMMARY_CHARS = 2000


def make_note_edit_tools(java_client: JavaClient, cache: ContentCache, note_content_updates: list | None = None) -> list:
    # 本次请求里 create_note 新建的笔记：往里写时不存撤销快照（撤销会把新笔记清空成空笔记）。
    # 只在一次请求内有效：中途弹确认卡片、确认后续跑是新请求，集合会丢——罕见路径，接受。
    created_note_ids: set[int] = set()

    @tool
    async def read_note_blocks(state: Annotated[AgentState, InjectedState], note_id: int | None = None) -> str:
        """读取笔记正文的块视图：每块一行"[块id | 类型 | hash=指纹] 内容"，内容是 Markdown。
        要修改笔记正文（改写、删除、移动、插入、把回复内容写进笔记）之前必须先调用它，拿到最新的块 id 和 hash。
        note_id 不传时读当前正在编辑的笔记。"""
        target = note_id or state.get("current_note_id")
        if not target:
            return "需要提供 note_id（当前也没有正在编辑的笔记）。"
        try:
            view = await java_client.get_note_blocks(state["username"], target)
        except Exception as e:
            return f"读取笔记块失败：{e}"
        if view is None:
            return f"找不到 id={target} 的笔记（不存在或不属于当前用户）。"
        lines = [
            f"[{b['id']} | {b['type']} | hash={b['hash']}]{'' if b['editable'] else '（只能删除/移动）'} {b['text']}"
            for b in view["blocks"]
        ]
        text = f"《{view['title']}》（笔记 id={target}，格式 {view['contentType']}，共 {len(lines)} 块）\n" + "\n".join(lines)
        if len(text) <= OFFLOAD_THRESHOLD_CHARS:
            return text
        cache_id = cache.put(text)
        return (f"块视图较长（{len(text)}字），已缓存（cache_id={cache_id}）。开头部分：\n{text[:SUMMARY_CHARS]}...\n"
                f"用 read_cached_content(cache_id=\"{cache_id}\", offset=...) 分段读完再修改。")

    @tool
    async def edit_note(
        operations: list[dict], state: Annotated[AgentState, InjectedState], note_id: int | None = None,
    ) -> str:
        """修改笔记正文（直接生效，用户可在回复下方一键撤销本轮修改）。operations 是按顺序执行的操作列表，
        要么全部成功要么全部不生效：
        - {"op":"insert","after":"<块id>|start|end","markdown":"..."}  在某块之后/开头/末尾插入（可多段）
        - {"op":"replace","blockId":"<块id>","hash":"<该块hash>","markdown":"..."}  用新内容替换该块（可变成多块）
        - {"op":"delete","blockIds":["<块id>",...],"hashes":["<对应hash>",...]}
        - {"op":"move","blockId":"<块id>","hash":"<该块hash>","after":"<块id>|start|end"}
        - {"op":"copy","fromNoteId":<来源笔记id>,"blockIds":["<来源块id>",...],"after":"<块id>|start|end"}
          把来源笔记（可以就是本篇）的块原样复制过来，按 blockIds 的顺序插入；来源块 id 来自对来源笔记的
          read_note_blocks(note_id=来源id)。图片、视频、网络视频、媒体集等非文字块要放进另一篇时用 copy，
          不要用 Markdown 重写（图片也用 copy，才能保留宽度等设置）；录音/音频块不能复制。只支持 Editor.js 笔记。
        块 id 和 hash 必须来自最近一次 read_note_blocks；标注"只能删除/移动"的块不能 replace；同一批里同一块只能被replace/delete/move 引用一次。
        copy 的来源块 id 来自对来源笔记的读取、after 来自对目标笔记的读取（跨笔记复制且要插到指定位置时，两篇都要读）；
        copy 不受"只能引用一次"限制，同一个来源块可以复制多次。
        markdown 支持段落、#标题、列表、- [ ] 任务、| 表格 |、```代码```、> 引用、---、![图注](图片地址)。
        用户要求把你回复中的内容写进笔记时，用 insert 写入对应的 Markdown；位置不明确时插到 end。
        失败（比如提示文档被修改过）时重新 read_note_blocks 后再改。note_id 不传时改当前正在编辑的笔记。"""
        target = note_id or state.get("current_note_id")
        if not target:
            return "需要提供 note_id（当前也没有正在编辑的笔记）。"
        if not operations:
            return "operations 不能为空。"
        try:
            result = await java_client.edit_note(
                state["username"], target, state.get("conversation_id"), operations,
                snapshot=target not in created_note_ids,
            )
        except Exception as e:
            return f"修改笔记失败：{e}"
        if note_content_updates is not None:
            note_content_updates.append({"noteId": target, "data": result})
        return (f"已修改笔记 id={target}（{len(operations)} 个操作）。块 id 和 hash 已经变化，"
                f"如需继续修改请先重新 read_note_blocks。")

    @tool
    async def create_note(title: str, state: Annotated[AgentState, InjectedState]) -> str:
        """新建一篇空白笔记（Editor.js，不公开、无标签、无项目），返回新笔记 id。用于"翻译成另一篇""另存为
        新笔记"等：建好后用 edit_note(note_id=新id) 往里写内容。title 写合适的标题（翻译时用译文标题）。
        不会切换用户当前打开的笔记，也不要让用户去切换。"""
        try:
            note = await java_client.create_note(state["username"], title)
        except Exception as e:
            return f"新建笔记失败：{e}"
        note_id = note["noteId"]
        created_note_ids.add(note_id)
        if note_content_updates is not None:
            note_content_updates.append({"type": "note_created", "noteId": note_id, "title": note["title"]})
        return (f"已新建笔记《{note['title']}》（id={note_id}）。现在可以用 edit_note(note_id={note_id}) 往里写内容；"
                f"不要切换用户当前打开的笔记。")

    return [read_note_blocks, edit_note, create_note]
