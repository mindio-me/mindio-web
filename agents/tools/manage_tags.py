"""标签管理：列出、新建、删除标签，给笔记 / 收藏加减标签。

新建标签、加减标签直接生效（随时能改回来）；删除标签会把它从所有笔记和收藏上一并去掉、
无法撤销，所以先弹确认卡片，用户接受后才删。每次改动都往 tag_update_sink 里记一笔
（type="tags_updated"），由 chat 路由推给前端刷新标签列表和当前笔记的标签。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from langgraph.types import interrupt

from app.java_client import JavaClient, JavaClientError
from app.state import AgentState


def _clean(names: list[str] | None) -> list[str]:
    return [n.strip() for n in (names or []) if n and n.strip()]


def make_tag_tools(java_client: JavaClient, tag_update_sink: list | None = None):
    def notify(note_id: int | None = None, clip_id: int | None = None) -> None:
        if tag_update_sink is not None:
            tag_update_sink.append({"type": "tags_updated", "noteId": note_id, "clipId": clip_id})

    @tool
    async def list_tags(state: Annotated[AgentState, InjectedState]) -> str:
        """列出用户的全部标签，以及每个标签被多少篇笔记、多少条收藏使用。整理标签、
        新建或删除标签之前先调用它，确认标签的准确名字。"""
        try:
            tags = await java_client.list_tags(state["username"])
        except JavaClientError as e:
            return f"读取标签失败：{e}"
        if not tags:
            return "用户还没有任何标签。"
        lines = [f"- {t['name']}（笔记 {t.get('noteCount', 0)} 篇，收藏 {t.get('clipCount', 0)} 条）" for t in tags]
        return f"共 {len(tags)} 个标签：\n" + "\n".join(lines)

    @tool
    async def create_tag(
        name: str, state: Annotated[AgentState, InjectedState], for_clips: bool = False
    ) -> str:
        """新建一个标签。for_clips=True 表示给收藏用（会出现在收藏的标签列表里），默认给笔记用。
        只是要给某篇笔记/收藏打标签时不必先建，直接用 tag_note / tag_clip，不存在的标签会自动新建。"""
        try:
            result = await java_client.create_tag(state["username"], name, "clip" if for_clips else "note")
        except JavaClientError as e:
            return f"新建标签失败：{e}"
        if not result.get("created"):
            return f"标签「{result['name']}」已经存在，没有重复新建。"
        notify()
        return f"已新建标签「{result['name']}」。"

    @tool
    async def delete_tag(name: str, state: Annotated[AgentState, InjectedState]) -> str:
        """删除一个标签：它会从所有笔记和收藏上一并去掉，不能撤销。只有用户明确要求删除某个
        标签时才调用；name 必须是 list_tags 里的准确名字。调用后会先弹出确认卡片，用户接受后才删除。"""
        name = (name or "").strip()
        if not name:
            return "标签名不能为空。"
        try:
            tags = await java_client.list_tags(state["username"])
        except JavaClientError as e:
            return f"读取标签失败：{e}"
        target = next((t for t in tags if t["name"] == name), None)
        if target is None:
            return f"没有名为「{name}」的标签，请先用 list_tags 确认准确名字。"

        decision = interrupt({
            "blockType": "deleteTag",
            "preview": {"name": name, "noteCount": target.get("noteCount", 0), "clipCount": target.get("clipCount", 0)},
        })
        if decision != "accept":
            return f"用户拒绝了删除，标签「{name}」保留。"

        try:
            await java_client.delete_tag(state["username"], name)
        except JavaClientError as e:
            return f"删除失败：{e}"
        notify()
        return f"已删除标签「{name}」。"

    @tool
    async def tag_note(
        state: Annotated[AgentState, InjectedState],
        add: list[str] | None = None,
        remove: list[str] | None = None,
        note_id: int | None = None,
    ) -> str:
        """给笔记加上 / 去掉标签。note_id 不填就是用户当前打开的笔记；要改别的笔记，先用
        search_workspace 或 read_note 拿到它的 id。add 里还不存在的标签会自动新建。"""
        target = note_id or state.get("current_note_id")
        if not target:
            return "当前没有打开的笔记，请告诉我要改哪篇笔记。"
        add, remove = _clean(add), _clean(remove)
        if not add and not remove:
            return "没有要添加或去掉的标签。"
        try:
            result = await java_client.update_note_tags(state["username"], target, add, remove)
        except JavaClientError as e:
            return f"修改笔记标签失败：{e}"
        notify(note_id=target)
        tags = result.get("tags") or []
        return f"笔记（id={target}）现在的标签：{'、'.join(tags) if tags else '无'}。"

    @tool
    async def tag_clip(
        clip_id: int,
        state: Annotated[AgentState, InjectedState],
        add: list[str] | None = None,
        remove: list[str] | None = None,
    ) -> str:
        """给收藏加上 / 去掉标签。clip_id 可以从 list_clips 或 search_workspace 的结果里拿到。
        add 里还不存在的标签会自动新建。"""
        add, remove = _clean(add), _clean(remove)
        if not add and not remove:
            return "没有要添加或去掉的标签。"
        try:
            result = await java_client.update_clip_tags(state["username"], clip_id, add, remove)
        except JavaClientError as e:
            return f"修改收藏标签失败：{e}"
        notify(clip_id=clip_id)
        tags = result.get("tags") or []
        return f"收藏（id={clip_id}）现在的标签：{'、'.join(tags) if tags else '无'}。"

    return [list_tags, create_tag, delete_tag, tag_note, tag_clip]
