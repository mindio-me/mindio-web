# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""LangGraph agent图：模型节点+工具节点+条件边，agent循环由LangGraph本身驱动
（不再手写for循环）。循环上限不设硬编码次数，改用recursion_limit兜底熔断（在
调用方compile/invoke时传入，见RECURSION_LIMIT），只用来拦截明显失控的场景，
正常的深度多轮研究不应该触碰到它。

`build_graph_builder` 只依赖一个已经绑好工具的model和工具列表，方便测试（可以传入
假的chat model）；真正生产用的组装（用哪个provider、真实工具）在
`build_default_graph_builder` 里。checkpointer不在这里绑定——它是per-conversation的
（JavaBackedCheckpointer需要按conversation_id构造+hydrate/flush），由调用方
（Task 6的流式接口）在处理每一轮对话时自己compile。
"""
from __future__ import annotations

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import SystemMessage
from langgraph.graph import END, StateGraph
from langgraph.prebuilt import ToolNode

from app.asr_client import default_asr_client
from app.java_client import JavaClient
from app.models import resolve_chat_model
from app.state import AgentState
from tools.add_checklist_item import make_add_checklist_item_tool
from tools.add_gallery_item import make_add_gallery_item_tool
from tools.add_reference_item import make_add_reference_item_tool
from tools.add_timeline_item import make_add_timeline_item_tool
from tools.analyze_image import make_analyze_image_tool
from tools.content_cache import ContentCache
from tools.get_project_notes import make_get_project_notes_tool
from tools.list_clips import make_list_clips_tool
from tools.manage_tags import make_tag_tools
from tools.read_cached_content import make_read_cached_content_tool
from tools.read_document import make_read_clip_tool, make_read_note_tool
from tools.read_note_references import make_read_note_references_tool
from tools.search_web import make_search_web_tool
from tools.search_workspace import make_search_workspace_tool
from tools.transcribe_audio import make_transcribe_audio_tool
from tools.update_project_field import make_update_project_field_tool
from tools.update_todo_list import update_todo_list
from tools.view_image import make_view_image_tool
from tools.edit_image import make_edit_image_tools
from tools.edit_note import make_note_edit_tools

SYSTEM_PROMPT = (
    "你是这款笔记应用内置的AI助手，名叫米欧（英文名 Mio；用户用英文交流时自称 Mio），可以自由对话、协助写作和总结。"
    "如果下面提供了笔记/收藏的相关内容，可以参考它们来回答，但不要虚构未提供的内容。"
    "写深度研究类笔记时，你可以综合使用这些信息来源：search_workspace（在用户自己的笔记和"
    "收藏里做语义搜索，返回片段）、read_note/read_clip（读取任意一篇笔记/收藏的全文）、"
    "search_web（联网搜索）、read_note_references（这篇笔记已经关联的素材全文）。"
    "你可以读取用户的任何一篇笔记，不需要先把它关联进当前笔记：用户在对话里点名某篇笔记"
    "（比如《xxx》）时，直接用 read_note(title=...) 读全文再回答；search_workspace 搜到的"
    "片段不够时，按片段前标注的 id 调 read_note/read_clip 读整篇。不要只凭片段或标题作答，"
    "也不要让用户把内容贴进来或关联进笔记。"
    "用户要按网站/域名、标签或关键词列出收藏（比如“github 上的所有收藏”“打了‘开源’标签的收藏”）时，"
    "用 list_clips 精确筛选，不要用 search_workspace——语义搜索只返回少量片段，列不全。"
    "当前笔记的正文如果在系统提示里被截断（以“内容过长，已截断”结尾），需要后半部分时"
    "也可以用 read_note 按当前笔记的标题读全文。"
    "用户要求总结/整理关联资料时，给出结构清晰、可以直接放进笔记正文的文本。"
    "如果用户明确要求把某条资料/视频/时间点/任务添加进当前笔记，可以调用"
    "add_reference_item/add_gallery_item/add_timeline_item/add_checklist_item——这些工具"
    "只能新增内容，不能修改或删除已有条目，调用后会先弹出确认卡片，用户接受后才真正写入。"
    "每次只调用一个写入工具，等这条确认完再决定要不要添加下一条。"
    "用户要整理标签时：list_tags 查看全部标签，create_tag 新建，delete_tag 删除（会先弹确认卡片），"
    "tag_note/tag_clip 给笔记或收藏加上、去掉标签（不存在的标签会自动新建）。"
    "不要在用户没有要求时自作主张删除或改动标签。"
    '如果用户想让你看看笔记里的图片内容（比如"这张图是什么"、"帮我描述一下这张照片"），'
    "调用 analyze_image；这个工具会直接把描述写进图片下方，不需要用户额外确认。"
    "如果只是需要了解某张图的内容来回答问题或写作（比如用户说参考某篇笔记/收藏里的截图、"
    "架构图，或者 read_note/read_clip 提示文档里有图片），调用 view_image 带着具体问题去看，"
    "它可以看任意一篇笔记或收藏里的图片，只读、不改动笔记。不要因为看不到图就让用户自己描述图片。"
    "如果用户要求把图片放进笔记、删掉某张图或给图片写图注，直接调用 insert_image / delete_image / "
    "set_image_caption（都直接生效，不需要用户确认），不要说做不到、也不要让用户自己粘贴。"
    "插图前如果不确定来源图片的序号，先用 view_image 不带 index 列出图片；正文里有【配图位】之类的"
    "占位段落时，把整段占位文字作为 anchor_text，图片会替换掉占位段。一次插多张就逐张调用。"
    '如果用户想把笔记里的一段音频/录音转成文字（比如"帮我转录一下这段录音"、"这段语音'
    '说了什么"），调用 transcribe_audio；这个工具会把音频交给后台转写，几十秒到几分钟后'
    "逐字稿和总结会显示在音频下方，不需要用户额外确认。不要在回复里编造转写内容。"
    '\n\n关于项目页：当用户正在编辑某个项目（系统提示里会出现"【当前正在编辑的项目】"这段）'
    "并要求起草/润色项目简介时，优先调用 get_project_notes 看有没有显式关联的笔记；"
    "没有或不够时可以再用 search_workspace 按项目名/关键词搜索可能相关但未显式关联的笔记；"
    "确实找不到任何相关材料就如实告诉用户，不要凭空编造。写入时只调用 update_project_field，"
    "且只写用户当前对话语言对应的那个字段（中文对话写 descriptionZh，英文对话写 description）。"
    "\n\n关于直接编辑笔记正文：用户要求改写、精简、合并、删除、调整顺序、补充内容，或者把你回复中的内容"
    "写进笔记时，先调用 read_note_blocks 读取块视图，再用 edit_note 一次提交需要的操作，直接生效，不需要"
    "用户确认（用户可以在回复下方一键撤销本轮修改）。不要说做不到，也不要让用户自己复制粘贴。"
    "只改用户要求改的部分；改完简要说明改了哪里。edit_note 提示文档被修改过时，重新读取后再改。"
    "\n\n关于把内容放进另一篇笔记/新建笔记：要把某篇笔记里的图片、视频、网络视频、媒体集等放进另一篇（包括"
    "当前笔记）时，先 read_note_blocks(note_id=来源) 找到块 id，再用 edit_note 的 copy 操作原样复制，不要用"
    "Markdown 重写。用户要求“翻译成另一篇”“另存为新笔记”时：先 read_note_blocks 读来源，再 create_note "
    "起合适的标题（翻译时用译文标题），然后对新笔记 edit_note，按来源块的顺序，文字块用 insert 写译文、"
    "非文字块用 copy 原样搬，after 一律用 end；内容多时可以分几批提交。不要切换用户当前打开的笔记，"
    "完成后在回复里说出新笔记的标题。录音/音频块不能复制，遇到时告诉用户。"
)

RECURSION_LIMIT = 30  # 兜底熔断，正常深度研究不应该触碰到这个上限


def build_graph_builder(model: BaseChatModel, tools: list) -> StateGraph:
    def call_model(state: AgentState) -> dict:
        system_text = SYSTEM_PROMPT
        if state.get("current_note_context"):
            system_text += f"\n\n【当前正在编辑的笔记】\n{state['current_note_context']}"
        if state.get("current_project_context"):
            system_text += f"\n\n【当前正在编辑的项目】\n{state['current_project_context']}"
        response = model.invoke([SystemMessage(system_text), *state["messages"]])
        return {"messages": [response]}

    def route_after_model(state: AgentState) -> str:
        last = state["messages"][-1]
        if getattr(last, "tool_calls", None):
            return "tools"
        return END

    graph = StateGraph(AgentState)
    graph.add_node("model", call_model)
    graph.add_node("tools", ToolNode(tools))
    graph.add_conditional_edges("model", route_after_model, {"tools": "tools", END: END})
    graph.add_edge("tools", "model")
    graph.set_entry_point("model")
    return graph


def build_default_graph_builder(
    java_client: JavaClient, provider: str,
    citations_sink: list | None = None, block_update_sink: list | None = None,
    media_block_update_sink: list | None = None, project_field_update_sink: list | None = None,
    note_content_update_sink: list | None = None,
) -> tuple[StateGraph, list]:
    cache = ContentCache()
    tools = [
        make_search_workspace_tool(java_client, cache, citations_sink=citations_sink),
        make_search_web_tool(java_client, citations_sink=citations_sink),
        make_read_note_references_tool(java_client, cache),
        make_read_note_tool(java_client, cache),
        make_read_clip_tool(java_client, cache),
        make_list_clips_tool(java_client),
        make_read_cached_content_tool(cache),
        update_todo_list,
        make_add_reference_item_tool(java_client, block_update_sink),
        make_add_gallery_item_tool(java_client, block_update_sink),
        make_add_timeline_item_tool(java_client, block_update_sink),
        make_add_checklist_item_tool(java_client, block_update_sink),
        make_analyze_image_tool(java_client, media_block_update_sink),
        make_view_image_tool(java_client),
        *make_edit_image_tools(java_client, note_content_update_sink),
        *make_note_edit_tools(java_client, cache, note_content_update_sink),
        make_transcribe_audio_tool(java_client, default_asr_client, media_block_update_sink),
        make_get_project_notes_tool(java_client),
        make_update_project_field_tool(java_client, project_field_update_sink),
        # 标签改动和正文改动一样立即落库，走同一个 sink，确认卡片 / 出错前也会先推给前端
        *make_tag_tools(java_client, note_content_update_sink),
    ]
    model = resolve_chat_model(provider).bind_tools(tools, parallel_tool_calls=False)
    return build_graph_builder(model, tools), tools
