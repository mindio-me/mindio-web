# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import InMemorySaver

from app.graph import build_graph_builder
from tools.analyze_image import make_analyze_image_tool


class FakeJavaClient:
    def __init__(self, media_blocks=None, media=None):
        self.media_blocks = media_blocks or []
        self.media = media or {"mimeType": "image/png", "base64Data": "abc"}
        self.patched: list[tuple] = []

    async def list_media_blocks(self, note_id):
        return self.media_blocks

    async def get_block_media(self, note_id, block_id):
        return self.media

    async def patch_media_block(self, note_id, block_id, fields):
        self.patched.append((note_id, block_id, fields))
        return {"url": "a.png", **fields}


def _agent_input(text: str, current_note_id: int | None = None) -> dict:
    return {
        "messages": [HumanMessage(text)], "todos": [], "conversation_id": "alice",
        "username": "alice", "current_note_context": None, "current_note_id": current_note_id,
    }


def _run_config() -> dict:
    return {"configurable": {"thread_id": "alice"}}


async def test_analyze_image_single_image_writes_caption_directly(monkeypatch):
    import tools.analyze_image as analyze_image_route
    monkeypatch.setattr(
        analyze_image_route, "resolve_chat_model",
        lambda provider: FakeCaptionModel("一张手写笔记的照片"),
    )
    java = FakeJavaClient(media_blocks=[{"blockId": "b1", "blockType": "image", "url": "a.png"}])
    media_updates: list = []
    tool_ = make_analyze_image_tool(java, media_updates)
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "analyze_image", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="已经描述好了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("看看这张图", current_note_id=9), config=_run_config())

    assert java.patched == [(9, "b1", {"caption": "一张手写笔记的照片"})]
    assert media_updates == [{
        "noteId": 9, "blockId": "b1", "blockType": "image",
        "data": {"url": "a.png", "caption": "一张手写笔记的照片"},
    }]
    assert result["messages"][-1].content == "已经描述好了"


async def test_analyze_image_no_current_note_returns_message_without_writing():
    java = FakeJavaClient()
    tool_ = make_analyze_image_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "analyze_image", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("看看这张图", current_note_id=None), config=_run_config())

    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "没有打开的笔记" in tool_messages[0].content
    assert java.patched == []


async def test_analyze_image_no_images_in_note_returns_message():
    java = FakeJavaClient(media_blocks=[{"blockId": "b1", "blockType": "audio", "url": "a.mp3"}])
    tool_ = make_analyze_image_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "analyze_image", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("看看这张图", current_note_id=9), config=_run_config())

    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "没有图片" in tool_messages[0].content


async def test_analyze_image_multiple_images_without_index_asks_for_clarification():
    java = FakeJavaClient(media_blocks=[
        {"blockId": "b1", "blockType": "image", "url": "a.png"},
        {"blockId": "b2", "blockType": "image", "url": "b.png"},
    ])
    tool_ = make_analyze_image_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "analyze_image", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="好的，麻烦你告诉我是第几张")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("看看图片", current_note_id=9), config=_run_config())

    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "2张图片" in tool_messages[0].content
    assert java.patched == []


async def test_analyze_image_multiple_images_with_index_picks_correct_one(monkeypatch):
    import tools.analyze_image as analyze_image_route
    monkeypatch.setattr(
        analyze_image_route, "resolve_chat_model",
        lambda provider: FakeCaptionModel("第二张图的描述"),
    )
    java = FakeJavaClient(media_blocks=[
        {"blockId": "b1", "blockType": "image", "url": "a.png"},
        {"blockId": "b2", "blockType": "image", "url": "b.png"},
    ])
    tool_ = make_analyze_image_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "analyze_image", "args": {"index": 2}, "id": "c1"}])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    await graph.ainvoke(_agent_input("看看第二张图", current_note_id=9), config=_run_config())

    assert java.patched == [(9, "b2", {"caption": "第二张图的描述"})]


async def test_analyze_image_model_failure_returns_friendly_message_without_writing(monkeypatch):
    import tools.analyze_image as analyze_image_route
    monkeypatch.setattr(
        analyze_image_route, "resolve_chat_model", lambda provider: FailingModel(),
    )
    java = FakeJavaClient(media_blocks=[{"blockId": "b1", "blockType": "image", "url": "a.png"}])
    tool_ = make_analyze_image_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "analyze_image", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("看看这张图", current_note_id=9), config=_run_config())

    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "图片分析失败" in tool_messages[0].content
    assert java.patched == []


class FakeCaptionModel:
    def __init__(self, content):
        self._content = content

    async def ainvoke(self, messages):
        return AIMessage(content=self._content)


class FailingModel:
    async def ainvoke(self, messages):
        raise RuntimeError("no vision support")
