"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from app.graph import build_graph_builder
from tools.manage_tags import make_tag_tools


class FakeJavaClient:
    def __init__(self):
        self.tags = [{"id": 1, "name": "研究", "noteCount": 3, "clipCount": 2}]
        self.calls: list[tuple] = []

    async def list_tags(self, username):
        return self.tags

    async def create_tag(self, username, name, scope="note"):
        self.calls.append(("create", name, scope))
        return {"id": 2, "name": name, "created": True}

    async def delete_tag(self, username, name):
        self.calls.append(("delete", name))
        return {"name": name}

    async def update_note_tags(self, username, note_id, add, remove):
        self.calls.append(("note", note_id, add, remove))
        return {"noteId": note_id, "tags": sorted(add)}

    async def update_clip_tags(self, username, clip_id, add, remove):
        self.calls.append(("clip", clip_id, add, remove))
        return {"clipId": clip_id, "tags": []}


def _agent_input(text: str, current_note_id: int | None = None) -> dict:
    return {
        "messages": [HumanMessage(text)],
        "todos": [],
        "conversation_id": "alice",
        "username": "alice",
        "current_note_context": None,
        "current_note_id": current_note_id,
        "current_project_context": None,
        "current_project_id": None,
    }


def _config() -> dict:
    return {"configurable": {"thread_id": "alice"}}


def _graph(java, sink, *tool_calls_then_text):
    model = FakeMessagesListChatModel(responses=list(tool_calls_then_text))
    return build_graph_builder(model, make_tag_tools(java, sink)).compile(checkpointer=InMemorySaver())


def _call(name, args):
    return AIMessage(content="", tool_calls=[{"name": name, "args": args, "id": "c1"}])


async def test_delete_tag_asks_for_confirmation_then_deletes_on_accept():
    java, sink = FakeJavaClient(), []
    graph = _graph(java, sink, _call("delete_tag", {"name": "研究"}), AIMessage(content="删好了"))

    first = await graph.ainvoke(_agent_input("删掉研究标签"), config=_config())

    assert first["__interrupt__"][0].value == {
        "blockType": "deleteTag", "preview": {"name": "研究", "noteCount": 3, "clipCount": 2},
    }
    assert java.calls == [] and sink == []

    await graph.ainvoke(Command(resume="accept"), config=_config())

    assert java.calls == [("delete", "研究")]
    assert sink == [{"type": "tags_updated", "noteId": None, "clipId": None}]


async def test_delete_tag_rejected_keeps_tag():
    java, sink = FakeJavaClient(), []
    graph = _graph(java, sink, _call("delete_tag", {"name": "研究"}), AIMessage(content="好的"))
    await graph.ainvoke(_agent_input("删掉研究标签"), config=_config())

    await graph.ainvoke(Command(resume="reject"), config=_config())

    assert java.calls == [] and sink == []


async def test_delete_unknown_tag_does_not_interrupt():
    java, sink = FakeJavaClient(), []
    graph = _graph(java, sink, _call("delete_tag", {"name": "不存在"}), AIMessage(content="没有这个标签"))

    result = await graph.ainvoke(_agent_input("删掉不存在标签"), config=_config())

    assert "__interrupt__" not in result
    assert java.calls == []


async def test_tag_note_defaults_to_current_note_and_notifies_frontend():
    java, sink = FakeJavaClient(), []
    graph = _graph(java, sink, _call("tag_note", {"add": [" 研究 ", ""], "remove": ["旧"]}), AIMessage(content="好了"))

    await graph.ainvoke(_agent_input("给这篇加上研究标签", current_note_id=9), config=_config())

    assert java.calls == [("note", 9, ["研究"], ["旧"])]
    assert sink == [{"type": "tags_updated", "noteId": 9, "clipId": None}]


async def test_tag_clip_and_create_tag_write_directly():
    java, sink = FakeJavaClient(), []
    graph = _graph(
        java, sink,
        _call("create_tag", {"name": "待读", "for_clips": True}),
        _call("tag_clip", {"clip_id": 7, "add": ["待读"]}),
        AIMessage(content="好了"),
    )

    await graph.ainvoke(_agent_input("建个待读标签并打到收藏7上"), config=_config())

    assert java.calls == [("create", "待读", "clip"), ("clip", 7, ["待读"], [])]
    assert sink == [
        {"type": "tags_updated", "noteId": None, "clipId": None},
        {"type": "tags_updated", "noteId": None, "clipId": 7},
    ]
