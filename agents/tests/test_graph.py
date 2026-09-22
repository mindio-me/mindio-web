# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

import pytest
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langchain_core.tools import tool
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.errors import GraphRecursionError

from app.graph import RECURSION_LIMIT, build_graph_builder


@tool
def dummy_tool(x: str) -> str:
    """A dummy tool for graph routing tests."""
    return f"dummy result for {x}"


def _agent_input(text: str) -> dict:
    return {
        "messages": [HumanMessage(text)],
        "todos": [],
        "conversation_id": "alice",
        "username": "alice",
        "current_note_context": None,
    }


def _run_config(recursion_limit: int = RECURSION_LIMIT) -> dict:
    return {"configurable": {"thread_id": "alice"}, "recursion_limit": recursion_limit}


def test_plain_text_response_ends_graph_without_calling_tools():
    model = FakeMessagesListChatModel(responses=[AIMessage(content="直接回答，不需要工具")])
    graph = build_graph_builder(model, [dummy_tool]).compile(checkpointer=InMemorySaver())

    result = graph.invoke(_agent_input("你好"), config=_run_config())

    assert result["messages"][-1].content == "直接回答，不需要工具"


def test_tool_call_response_routes_through_tools_and_back_to_model():
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "dummy_tool", "args": {"x": "hi"}, "id": "call-1"}
    ])
    final_msg = AIMessage(content="根据工具结果给出的最终回答")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [dummy_tool]).compile(checkpointer=InMemorySaver())

    result = graph.invoke(_agent_input("需要查一下"), config=_run_config())

    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert len(tool_messages) == 1
    assert "dummy result for hi" in tool_messages[0].content
    assert result["messages"][-1].content == "根据工具结果给出的最终回答"


def test_runaway_tool_calling_hits_recursion_limit_safety_net():
    # 让fake model无论问几次都返回一个tool_call，模拟"失控循环"场景；每条响应要用不同的
    # tool_call id构造成不同的消息对象——LangGraph的消息reducer按id去重/合并，如果重用同一个
    # AIMessage对象会被当成"同一条消息的更新"而不是新的一轮，看不出真实的循环行为。
    responses = [
        AIMessage(content="", tool_calls=[{"name": "dummy_tool", "args": {"x": "loop"}, "id": f"call-{i}"}])
        for i in range(100)
    ]
    model = FakeMessagesListChatModel(responses=responses)
    graph = build_graph_builder(model, [dummy_tool]).compile(checkpointer=InMemorySaver())

    with pytest.raises(GraphRecursionError):
        graph.invoke(_agent_input("触发死循环"), config=_run_config(recursion_limit=5))
