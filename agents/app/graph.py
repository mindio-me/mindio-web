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
from tools.read_cached_content import make_read_cached_content_tool
from tools.read_document import make_read_clip_tool, make_read_note_tool
from tools.read_note_references import make_read_note_references_tool
from tools.search_web import make_search_web_tool
from tools.search_workspace import make_search_workspace_tool
from tools.transcribe_audio import make_transcribe_audio_tool
from tools.update_project_field import make_update_project_field_tool
from tools.update_todo_list import update_todo_list
from tools.view_image import make_view_image_tool

SYSTEM_PROMPT = (
    "你是这款笔记应用内置的AI助手，可以自由对话、协助写作和总结。"
    "如果下面提供了笔记/收藏的相关内容，可以参考它们来回答，但不要虚构未提供的内容。"
    "写深度研究类笔记时，你可以综合使用这些信息来源：search_workspace（在用户自己的笔记和"
    "收藏里做语义搜索，返回片段）、read_note/read_clip（读取任意一篇笔记/收藏的全文）、"
    "search_web（联网搜索）、read_note_references（这篇笔记已经关联的素材全文）。"
    "你可以读取用户的任何一篇笔记，不需要先把它关联进当前笔记：用户在对话里点名某篇笔记"
    "（比如《xxx》）时，直接用 read_note(title=...) 读全文再回答；search_workspace 搜到的"
    "片段不够时，按片段前标注的 id 调 read_note/read_clip 读整篇。不要只凭片段或标题作答，"
    "也不要让用户把内容贴进来或关联进笔记。"
    "当前笔记的正文如果在系统提示里被截断（以“内容过长，已截断”结尾），需要后半部分时"
    "也可以用 read_note 按当前笔记的标题读全文。"
    "用户要求总结/整理关联资料时，给出结构清晰、可以直接放进笔记正文的文本。"
    "如果用户明确要求把某条资料/视频/时间点/任务添加进当前笔记，可以调用"
    "add_reference_item/add_gallery_item/add_timeline_item/add_checklist_item——这些工具"
    "只能新增内容，不能修改或删除已有条目，调用后会先弹出确认卡片，用户接受后才真正写入。"
    "每次只调用一个写入工具，等这条确认完再决定要不要添加下一条。"
    '如果用户想让你看看笔记里的图片内容（比如"这张图是什么"、"帮我描述一下这张照片"），'
    "调用 analyze_image；这个工具会直接把描述写进图片下方，不需要用户额外确认。"
    "如果只是需要了解某张图的内容来回答问题或写作（比如用户说参考某篇笔记/收藏里的截图、"
    "架构图，或者 read_note/read_clip 提示文档里有图片），调用 view_image 带着具体问题去看，"
    "它可以看任意一篇笔记或收藏里的图片，只读、不改动笔记。不要因为看不到图就让用户自己描述图片。"
    '如果用户想把笔记里的一段音频/录音转成文字（比如"帮我转录一下这段录音"、"这段语音'
    '说了什么"），调用 transcribe_audio；这个工具会把音频交给后台转写，几十秒到几分钟后'
    "逐字稿和总结会显示在音频下方，不需要用户额外确认。不要在回复里编造转写内容。"
    '\n\n关于项目页：当用户正在编辑某个项目（系统提示里会出现"【当前正在编辑的项目】"这段）'
    "并要求起草/润色项目简介时，优先调用 get_project_notes 看有没有显式关联的笔记；"
    "没有或不够时可以再用 search_workspace 按项目名/关键词搜索可能相关但未显式关联的笔记；"
    "确实找不到任何相关材料就如实告诉用户，不要凭空编造。写入时只调用 update_project_field，"
    "且只写用户当前对话语言对应的那个字段（中文对话写 descriptionZh，英文对话写 description）。"
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
) -> tuple[StateGraph, list]:
    cache = ContentCache()
    tools = [
        make_search_workspace_tool(java_client, cache, citations_sink=citations_sink),
        make_search_web_tool(java_client, citations_sink=citations_sink),
        make_read_note_references_tool(java_client, cache),
        make_read_note_tool(java_client, cache),
        make_read_clip_tool(java_client, cache),
        make_read_cached_content_tool(cache),
        update_todo_list,
        make_add_reference_item_tool(java_client, block_update_sink),
        make_add_gallery_item_tool(java_client, block_update_sink),
        make_add_timeline_item_tool(java_client, block_update_sink),
        make_add_checklist_item_tool(java_client, block_update_sink),
        make_analyze_image_tool(java_client, media_block_update_sink),
        make_view_image_tool(java_client),
        make_transcribe_audio_tool(java_client, default_asr_client, media_block_update_sink),
        make_get_project_notes_tool(java_client),
        make_update_project_field_tool(java_client, project_field_update_sink),
    ]
    model = resolve_chat_model(provider).bind_tools(tools, parallel_tool_calls=False)
    return build_graph_builder(model, tools), tools
