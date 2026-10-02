"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import InMemorySaver

from app.graph import build_graph_builder
from app.java_client import JavaClientError
from tools.update_project_field import make_update_project_field_tool


class FakeJavaClient:
    def __init__(self, raise_error: bool = False):
        self.raise_error = raise_error
        self.patched: list[tuple] = []

    async def patch_project_field(self, project_id, field, value):
        if self.raise_error:
            raise JavaClientError("boom")
        self.patched.append((project_id, field, value))
        return {"id": project_id, field: value}


def _agent_input(text: str, current_project_id: int | None = None) -> dict:
    return {
        "messages": [HumanMessage(text)], "todos": [], "conversation_id": "alice", "username": "alice",
        "current_note_context": None, "current_note_id": None,
        "current_project_context": None, "current_project_id": current_project_id,
    }


def _run_config() -> dict:
    return {"configurable": {"thread_id": "alice"}}


async def test_writes_field_directly_without_interrupt():
    java = FakeJavaClient()
    updates: list = []
    tool_ = make_update_project_field_tool(java, updates)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "update_project_field", "args": {"field": "descriptionZh", "value": "新简介"}, "id": "c1"}
    ])
    final_msg = AIMessage(content="已经写好了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("帮我写简介", current_project_id=9), config=_run_config())

    assert java.patched == [(9, "descriptionZh", "新简介")]
    assert updates == [{"projectId": 9, "field": "descriptionZh", "value": "新简介"}]
    assert result["messages"][-1].content == "已经写好了"


async def test_rejects_field_outside_allowed_list_without_calling_java():
    java = FakeJavaClient()
    updates: list = []
    tool_ = make_update_project_field_tool(java, updates)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "update_project_field", "args": {"field": "projectUrl", "value": "https://x.com"}, "id": "c1"}
    ])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    await graph.ainvoke(_agent_input("帮我改个链接", current_project_id=9), config=_run_config())

    assert java.patched == []
    assert updates == []


async def test_returns_guidance_when_no_current_project():
    java = FakeJavaClient()
    tool_ = make_update_project_field_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "update_project_field", "args": {"field": "descriptionZh", "value": "新简介"}, "id": "c1"}
    ])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    await graph.ainvoke(_agent_input("帮我写简介", current_project_id=None), config=_run_config())

    assert java.patched == []


async def test_java_error_does_not_crash_graph():
    java = FakeJavaClient(raise_error=True)
    tool_ = make_update_project_field_tool(java)
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "update_project_field", "args": {"field": "descriptionZh", "value": "新简介"}, "id": "c1"}
    ])
    final_msg = AIMessage(content="抱歉")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("帮我写简介", current_project_id=9), config=_run_config())

    assert result["messages"][-1].content == "抱歉"
