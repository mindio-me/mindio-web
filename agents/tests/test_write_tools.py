# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from app.graph import build_graph_builder
from tools.add_timeline_item import make_add_timeline_item_tool
from tools.add_reference_item import make_add_reference_item_tool
from tools.add_gallery_item import make_add_gallery_item_tool
from tools.add_checklist_item import make_add_checklist_item_tool


class FakeJavaClient:
    def __init__(self):
        self.appended: list[tuple] = []

    async def append_block_item(self, note_id, block_type, item):
        self.appended.append((note_id, block_type, item))
        return [item]


def _agent_input(text: str, current_note_id: int | None = None) -> dict:
    return {
        "messages": [HumanMessage(text)],
        "todos": [],
        "conversation_id": "alice",
        "username": "alice",
        "current_note_context": None,
        "current_note_id": current_note_id,
    }


def _run_config() -> dict:
    return {"configurable": {"thread_id": "alice"}}


# ── add_timeline_item：完整覆盖四种路径 ──────────────────────────

async def test_add_timeline_item_interrupts_before_writing():
    java = FakeJavaClient()
    tool_ = make_add_timeline_item_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_timeline_item", "args": {"date": "2024-01", "title": "事件一", "description": ""}, "id": "c1"}
    ])
    model = FakeMessagesListChatModel(responses=[tool_call_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("帮我加一条", current_note_id=9), config=_run_config())

    assert "__interrupt__" in result
    interrupt_value = result["__interrupt__"][0].value
    assert interrupt_value == {
        "blockType": "timeline", "noteId": 9,
        "preview": {"date": "2024-01", "title": "事件一", "description": ""}
    }
    assert java.appended == []


async def test_add_timeline_item_writes_after_accept():
    java = FakeJavaClient()
    block_updates: list = []
    tool_ = make_add_timeline_item_tool(java, block_updates)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_timeline_item", "args": {"date": "2024-01", "title": "事件一", "description": ""}, "id": "c1"}
    ])
    final_msg = AIMessage(content="已经加好了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())
    await graph.ainvoke(_agent_input("帮我加一条", current_note_id=9), config=_run_config())

    result = await graph.ainvoke(Command(resume="accept"), config=_run_config())

    assert java.appended == [(9, "timeline", {"date": "2024-01", "title": "事件一", "description": "", "link": ""})]
    assert result["messages"][-1].content == "已经加好了"
    # FakeJavaClient.append_block_item 固定返回 [item]，block_update_sink 应该原样
    # 捕获到这个返回值——这就是Finding 1修复要证明的：真实返回值真的被传出去了，
    # 不是随便塞了个占位符。
    assert block_updates == [{
        "noteId": 9, "blockType": "timeline",
        "items": [{"date": "2024-01", "title": "事件一", "description": "", "link": ""}]
    }]


async def test_add_timeline_item_skips_write_after_reject():
    java = FakeJavaClient()
    tool_ = make_add_timeline_item_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_timeline_item", "args": {"date": "2024-01", "title": "事件一", "description": ""}, "id": "c1"}
    ])
    final_msg = AIMessage(content="好的，不加了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())
    first_result = await graph.ainvoke(_agent_input("帮我加一条", current_note_id=9), config=_run_config())
    assert "__interrupt__" in first_result

    await graph.ainvoke(Command(resume="reject"), config=_run_config())

    assert java.appended == []


async def test_add_timeline_item_rejects_when_no_current_note():
    java = FakeJavaClient()
    tool_ = make_add_timeline_item_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_timeline_item", "args": {"date": "2024-01", "title": "事件一", "description": ""}, "id": "c1"}
    ])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("帮我加一条", current_note_id=None), config=_run_config())

    assert "__interrupt__" not in result
    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "没有打开的笔记" in tool_messages[0].content
    assert java.appended == []


# ── add_reference_item：核心路径 ──────────────────────────

async def test_add_reference_item_interrupts_then_writes_after_accept():
    java = FakeJavaClient()
    tool_ = make_add_reference_item_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_reference_item", "args": {
            "kind": "link", "title": "参考文章", "url": "https://example.com", "note": ""
        }, "id": "c1"}
    ])
    final_msg = AIMessage(content="已添加参考资料")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("加个参考", current_note_id=9), config=_run_config())
    assert "__interrupt__" in result

    await graph.ainvoke(Command(resume="accept"), config=_run_config())

    assert java.appended == [(9, "references", {
        "kind": "link", "title": "参考文章", "url": "https://example.com", "note": ""
    })]


async def test_add_reference_item_skips_write_after_reject():
    java = FakeJavaClient()
    tool_ = make_add_reference_item_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_reference_item", "args": {
            "kind": "link", "title": "参考文章", "url": "https://example.com", "note": ""
        }, "id": "c1"}
    ])
    final_msg = AIMessage(content="好的，不加了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())
    first_result = await graph.ainvoke(_agent_input("加个参考", current_note_id=9), config=_run_config())
    assert "__interrupt__" in first_result

    await graph.ainvoke(Command(resume="reject"), config=_run_config())

    assert java.appended == []


# ── add_gallery_item：核心路径 ──────────────────────────

async def test_add_gallery_item_interrupts_then_writes_after_accept():
    java = FakeJavaClient()
    tool_ = make_add_gallery_item_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_gallery_item", "args": {
            "type": "video", "embedUrl": "https://youtube.com/embed/x", "caption": ""
        }, "id": "c1"}
    ])
    final_msg = AIMessage(content="已添加到画廊")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("加个视频", current_note_id=9), config=_run_config())
    assert "__interrupt__" in result

    await graph.ainvoke(Command(resume="accept"), config=_run_config())

    assert java.appended == [(9, "mediaGallery", {
        "type": "video", "url": "", "embedUrl": "https://youtube.com/embed/x", "caption": ""
    })]


async def test_add_gallery_item_skips_write_after_reject():
    java = FakeJavaClient()
    tool_ = make_add_gallery_item_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_gallery_item", "args": {
            "type": "video", "embedUrl": "https://youtube.com/embed/x", "caption": ""
        }, "id": "c1"}
    ])
    final_msg = AIMessage(content="好的，不加了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())
    first_result = await graph.ainvoke(_agent_input("加个视频", current_note_id=9), config=_run_config())
    assert "__interrupt__" in first_result

    await graph.ainvoke(Command(resume="reject"), config=_run_config())

    assert java.appended == []


# ── add_checklist_item：核心路径 ──────────────────────────

async def test_add_checklist_item_interrupts_then_writes_after_accept():
    java = FakeJavaClient()
    tool_ = make_add_checklist_item_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_checklist_item", "args": {"text": "买菜", "checked": False}, "id": "c1"}
    ])
    final_msg = AIMessage(content="已添加任务")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("加个任务", current_note_id=9), config=_run_config())
    assert "__interrupt__" in result

    await graph.ainvoke(Command(resume="accept"), config=_run_config())

    assert java.appended == [(9, "checklist", {"text": "买菜", "checked": False})]


async def test_add_checklist_item_skips_write_after_reject():
    java = FakeJavaClient()
    tool_ = make_add_checklist_item_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_checklist_item", "args": {"text": "买菜", "checked": False}, "id": "c1"}
    ])
    final_msg = AIMessage(content="好的，不加了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())
    first_result = await graph.ainvoke(_agent_input("加个任务", current_note_id=9), config=_run_config())
    assert "__interrupt__" in first_result

    await graph.ainvoke(Command(resume="reject"), config=_run_config())

    assert java.appended == []


async def test_add_checklist_item_rejects_when_no_current_note():
    java = FakeJavaClient()
    tool_ = make_add_checklist_item_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_checklist_item", "args": {"text": "买菜", "checked": False}, "id": "c1"}
    ])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("加个任务", current_note_id=None), config=_run_config())

    assert "__interrupt__" not in result
    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "没有打开的笔记" in tool_messages[0].content
    assert java.appended == []
