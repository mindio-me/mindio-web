# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

import json

import pytest
from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langchain_core.tools import tool

import app.routes.chat as chat_route
from app.graph import build_graph_builder
from app.routes.chat import ChatStreamRequest, _dedupe_citations, _run_agent


class FakeJavaClient:
    def __init__(self):
        self._store: dict[str, str] = {}
        self.closed = False
        self._web_results: list[dict] = []

    async def get_state(self, conversation_id: str) -> str | None:
        return self._store.get(conversation_id)

    async def put_state(self, conversation_id: str, state_blob: str) -> None:
        self._store[conversation_id] = state_blob

    async def retrieve(self, username: str, query: str, top_k: int) -> list[dict]:
        return [{"chunkText": f"result for {query}", "sourceType": "NOTE", "sourceId": 1}]

    async def search_web(self, query: str, limit: int) -> list[dict]:
        return self._web_results

    async def append_block_item(self, note_id, block_type, item):
        return [item]

    async def aclose(self) -> None:
        self.closed = True


@tool
def dummy_tool(x: str) -> str:
    """dummy"""
    return f"dummy {x}"


async def _collect_events(request: ChatStreamRequest) -> list[dict]:
    lines = [line async for line in _run_agent(request)]
    return [json.loads(line) for line in lines]


@pytest.fixture
def fake_java_client(monkeypatch):
    fake = FakeJavaClient()
    monkeypatch.setattr(chat_route, "JavaClient", lambda: fake)
    return fake


def _patch_graph(monkeypatch, model, tools):
    def fake_builder(java_client, provider, citations_sink=None, block_update_sink=None, media_block_update_sink=None):
        return build_graph_builder(model, tools), tools

    monkeypatch.setattr(chat_route, "build_default_graph_builder", fake_builder)


async def test_plain_text_reply_streams_text_delta_then_done(monkeypatch, fake_java_client):
    model = GenericFakeChatModel(messages=iter([AIMessage(content="你好呀")]))
    _patch_graph(monkeypatch, model, [dummy_tool])

    events = await _collect_events(ChatStreamRequest(username="alice", content="hi", conversationId="alice"))

    assert events[-1]["type"] == "done"
    assert events[-1]["content"] == "你好呀"
    assert any(e["type"] == "text_delta" for e in events)
    assert not any(e["type"] == "error" for e in events)


async def test_search_workspace_tool_call_emits_tool_call_event(monkeypatch, fake_java_client):
    from tools.content_cache import ContentCache
    from tools.search_workspace import make_search_workspace_tool

    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "search_workspace", "args": {"query": "笔记"}, "id": "c1"}])
    final_msg = AIMessage(content="根据检索结果的回答")
    from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])

    # 用真实的 build_default_graph_builder 组装方式（而不是 _patch_graph 那个不接线
    # citations_sink 的简化版），只替换掉真正会打网络请求的模型解析这一步，这样
    # citations_sink 能像生产代码里一样被正确接到 search_workspace 工具上。
    def fake_builder(java_client, provider, citations_sink=None, block_update_sink=None, media_block_update_sink=None):
        search_tool = make_search_workspace_tool(java_client, ContentCache(), citations_sink=citations_sink)
        return build_graph_builder(model, [search_tool]), [search_tool]

    monkeypatch.setattr(chat_route, "build_default_graph_builder", fake_builder)

    events = await _collect_events(ChatStreamRequest(username="alice", content="帮我查一下", conversationId="alice"))

    tool_events = [e for e in events if e["type"] == "tool_call"]
    assert len(tool_events) == 1
    assert tool_events[0]["query"] == "笔记"
    assert events[-1]["type"] == "done"
    assert events[-1]["content"] == "根据检索结果的回答"
    assert events[-1]["citations"] == [{"sourceType": "NOTE", "sourceId": 1}]


async def test_search_web_tool_call_emits_tool_call_event_and_web_citation(monkeypatch, fake_java_client):
    from tools.search_web import make_search_web_tool

    fake_java_client._web_results = [{"title": "示例文章", "url": "https://example.com/a", "excerpt": "摘要"}]
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "search_web", "args": {"query": "用户增长"}, "id": "c1"}])
    final_msg = AIMessage(content="参考了一篇网文")
    from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])

    def fake_builder(java_client, provider, citations_sink=None, block_update_sink=None, media_block_update_sink=None):
        search_tool = make_search_web_tool(java_client, citations_sink=citations_sink)
        return build_graph_builder(model, [search_tool]), [search_tool]

    monkeypatch.setattr(chat_route, "build_default_graph_builder", fake_builder)

    events = await _collect_events(ChatStreamRequest(username="alice", content="帮我搜一下", conversationId="alice"))

    tool_events = [e for e in events if e["type"] == "tool_call"]
    assert len(tool_events) == 1
    assert tool_events[0]["query"] == "用户增长"
    assert events[-1]["citations"] == [
        {"sourceType": "WEB", "sourceUrl": "https://example.com/a", "title": "示例文章"}
    ]


async def test_graph_failure_yields_error_event_instead_of_raising(monkeypatch, fake_java_client):
    def broken_builder(java_client, provider, citations_sink=None, block_update_sink=None, media_block_update_sink=None):
        raise RuntimeError("boom")

    monkeypatch.setattr(chat_route, "build_default_graph_builder", broken_builder)

    events = await _collect_events(ChatStreamRequest(username="alice", content="hi", conversationId="alice"))

    assert len(events) == 1
    assert events[0]["type"] == "error"


def test_dedupe_citations_keys_web_by_source_url_not_source_id():
    # WEB citations都没有sourceId（永远是None），如果去重键退化回(sourceType, sourceId)，
    # 两条不同网页会被错误地合并成一条——这里保证它们按sourceUrl区分开，同时NOTE/CLIP
    # 的重复项仍然按sourceId正常去重。
    citations = [
        {"sourceType": "WEB", "sourceId": None, "sourceUrl": "https://example.com/a", "title": "文章A"},
        {"sourceType": "WEB", "sourceId": None, "sourceUrl": "https://example.com/b", "title": "文章B"},
        {"sourceType": "NOTE", "sourceId": 1},
        {"sourceType": "NOTE", "sourceId": 1},
        {"sourceType": "CLIP", "sourceId": 2},
    ]

    result = _dedupe_citations(citations)

    web_results = [c for c in result if c["sourceType"] == "WEB"]
    assert {c["sourceUrl"] for c in web_results} == {"https://example.com/a", "https://example.com/b"}
    assert len([c for c in result if c["sourceType"] == "NOTE"]) == 1
    assert len([c for c in result if c["sourceType"] == "CLIP"]) == 1
    assert len(result) == 4


async def test_conversation_state_persists_across_turns(monkeypatch, fake_java_client):
    model = GenericFakeChatModel(messages=iter([AIMessage(content="第一轮回复")]))
    _patch_graph(monkeypatch, model, [dummy_tool])

    await _collect_events(ChatStreamRequest(username="alice", content="第一句话", conversationId="alice"))

    assert "alice" in fake_java_client._store  # flush后应该已经把状态存回"Java"


async def test_write_tool_interrupt_emits_confirm_request_instead_of_done(monkeypatch, fake_java_client):
    from langgraph.types import interrupt

    @tool
    def add_timeline_item_stub(date: str, title: str) -> str:
        """stub write tool that always interrupts for confirmation"""
        decision = interrupt({"blockType": "timeline", "noteId": 9, "preview": {"date": date, "title": title}})
        return "accepted" if decision == "accept" else "rejected"

    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_timeline_item_stub", "args": {"date": "2024-01", "title": "事件一"}, "id": "call-1"}
    ])
    from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
    model = FakeMessagesListChatModel(responses=[tool_call_msg])
    _patch_graph(monkeypatch, model, [add_timeline_item_stub])

    events = await _collect_events(ChatStreamRequest(username="alice", content="加一条", conversationId="alice", currentNoteId=9))

    assert events[-1]["type"] == "confirm_request"
    assert events[-1]["blockType"] == "timeline"
    assert events[-1]["noteId"] == 9
    assert events[-1]["preview"] == {"date": "2024-01", "title": "事件一"}
    assert "proposalId" in events[-1]
    assert not any(e["type"] == "done" for e in events)


async def test_resume_accept_calls_tool_continuation_and_emits_done(monkeypatch, fake_java_client):
    from langgraph.types import interrupt

    @tool
    def add_timeline_item_stub(date: str, title: str) -> str:
        """stub"""
        decision = interrupt({"blockType": "timeline", "noteId": 9, "preview": {"date": date, "title": title}})
        return "已添加" if decision == "accept" else "用户拒绝了"

    from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_timeline_item_stub", "args": {"date": "2024-01", "title": "事件一"}, "id": "call-1"}
    ])
    final_msg = AIMessage(content="已经帮你加好了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    _patch_graph(monkeypatch, model, [add_timeline_item_stub])

    first_events = await _collect_events(ChatStreamRequest(username="alice", content="加一条", conversationId="alice"))
    proposal_id = first_events[-1]["proposalId"]

    from app.routes.chat import _resume_agent, ChatResumeRequest
    resume_lines = [line async for line in _resume_agent(
        ChatResumeRequest(conversationId="alice", proposalId=proposal_id, decision="accept")
    )]
    resume_events = [json.loads(line) for line in resume_lines]

    assert resume_events[-1]["type"] == "done"
    assert resume_events[-1]["content"] == "已经帮你加好了"


async def test_resume_accept_emits_block_updated_before_done(monkeypatch, fake_java_client):
    """证明Finding 1的修复：真实写入工具(add_timeline_item)接受后，block_update_sink
    捕获到的更新真的通过_drive_graph变成了block_updated SSE事件，顺序在done之前——
    不是用stub工具伪造，而是走生产代码里真正会用到的make_add_timeline_item_tool，
    确认java_client.append_block_item的返回值真的被传到了事件里。"""
    from tools.add_timeline_item import make_add_timeline_item_tool
    from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel

    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_timeline_item", "args": {"date": "2024-01", "title": "事件一", "description": ""}, "id": "call-1"}
    ])
    final_msg = AIMessage(content="已经帮你加好了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])

    def fake_builder(java_client, provider, citations_sink=None, block_update_sink=None, media_block_update_sink=None):
        write_tool = make_add_timeline_item_tool(java_client, block_update_sink)
        return build_graph_builder(model, [write_tool]), [write_tool]

    monkeypatch.setattr(chat_route, "build_default_graph_builder", fake_builder)

    first_events = await _collect_events(ChatStreamRequest(
        username="alice", content="加一条", conversationId="alice", currentNoteId=9
    ))
    assert first_events[-1]["type"] == "confirm_request"
    proposal_id = first_events[-1]["proposalId"]

    from app.routes.chat import _resume_agent, ChatResumeRequest
    resume_lines = [line async for line in _resume_agent(
        ChatResumeRequest(conversationId="alice", proposalId=proposal_id, decision="accept")
    )]
    resume_events = [json.loads(line) for line in resume_lines]

    event_types = [e["type"] for e in resume_events]
    assert "block_updated" in event_types
    assert event_types.index("block_updated") < event_types.index("done")

    block_updated_event = next(e for e in resume_events if e["type"] == "block_updated")
    assert block_updated_event["noteId"] == 9
    assert block_updated_event["blockType"] == "timeline"
    assert block_updated_event["items"] == [
        {"date": "2024-01", "title": "事件一", "description": "", "link": ""}
    ]

    assert resume_events[-1]["type"] == "done"
    assert resume_events[-1]["content"] == "已经帮你加好了"


async def test_stale_interrupt_is_auto_rejected_when_new_message_arrives(monkeypatch, fake_java_client):
    """复现真实报的bug：确认卡片被晾在那没点（刷新页面/切走），用户接着问了句完全
    不相关的话。不清理的话，图停在interrupt点留下的没配对ToolMessage的tool_calls
    AIMessage会让下一次模型调用直接报400——这条会话就永久卡死了。修复后，处理新消息
    前会先把这个遗留的interrupt自动当"拒绝"结清，用户看到的只是新消息的正常回复。"""
    from langgraph.types import interrupt

    decisions: list[str] = []

    @tool
    def add_timeline_item_stub(date: str, title: str) -> str:
        """stub write tool that always interrupts for confirmation"""
        decision = interrupt({"blockType": "timeline", "noteId": 9, "preview": {"date": date, "title": title}})
        decisions.append(decision)
        return "accepted" if decision == "accept" else "rejected"

    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_timeline_item_stub", "args": {"date": "2024-01", "title": "事件一"}, "id": "call-1"}
    ])
    after_reject_msg = AIMessage(content="好的，不加了")
    plain_reply_msg = AIMessage(content="没问题")
    from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
    model = FakeMessagesListChatModel(responses=[tool_call_msg, after_reject_msg, plain_reply_msg])
    _patch_graph(monkeypatch, model, [add_timeline_item_stub])

    first_events = await _collect_events(
        ChatStreamRequest(username="alice", content="加一条", conversationId="alice", currentNoteId=9)
    )
    assert first_events[-1]["type"] == "confirm_request"

    # 用户没点确认卡片，直接问了个不相关的新问题
    second_events = await _collect_events(
        ChatStreamRequest(username="alice", content="你有什么建议？", conversationId="alice")
    )

    assert not any(e["type"] == "error" for e in second_events)
    assert second_events[-1]["type"] == "done"
    assert second_events[-1]["content"] == "没问题"
    assert decisions == ["reject"]  # 遗留的interrupt被自动结清成"拒绝"，不是卡死


async def test_orphaned_tool_calls_without_interrupt_are_repaired_before_new_message(monkeypatch, fake_java_client):
    """复现真实撞到过的场景：某些provider即使传了parallel_tool_calls=False仍然一次
    发出多个tool_calls，其中的调用没有全部拿到结果（工具执行中途崩溃，或者像这里
    直接卡在"tools"节点还没跑）——graph.get_state()此时没有pending interrupt，
    没法用Command(resume=...)恢复（没有暂停的协程等着恢复）。走的是另一条
    update_state()修复路径：给每个没配对结果的tool_call补一条占位ToolMessage。"""
    from app.checkpointer import JavaBackedCheckpointer
    from app.graph import build_graph_builder
    from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel

    setup_model = FakeMessagesListChatModel(responses=[AIMessage(content="", tool_calls=[
        {"name": "dummy_tool", "args": {"x": "a"}, "id": "call-1"},
        {"name": "dummy_tool", "args": {"x": "b"}, "id": "call-2"},
    ])])
    setup_checkpointer = JavaBackedCheckpointer(fake_java_client, "alice")
    # interrupt_before在"tools"节点执行前静态暂停——不经过真的崩溃，直接构造出
    # 和真实故障完全同形的checkpoint：两个tool_calls，一个都没有ToolMessage，
    # state.next非空，但task.interrupts是空的（这和用户主动确认卡片那种
    # interrupt()动态暂停是两回事）。
    setup_graph = build_graph_builder(setup_model, [dummy_tool]).compile(
        checkpointer=setup_checkpointer, interrupt_before=["tools"]
    )
    config = {"configurable": {"thread_id": "alice"}}
    await setup_graph.ainvoke({
        "messages": [HumanMessage("帮我查两件事")], "todos": [], "conversation_id": "alice",
        "username": "alice", "current_note_context": None, "current_note_id": None,
    }, config=config)
    await setup_checkpointer.flush()

    state = setup_graph.get_state(config)
    assert state.next == ("tools",)
    assert not any(t.interrupts for t in state.tasks)

    final_reply2 = AIMessage(content="没问题")
    model2 = FakeMessagesListChatModel(responses=[final_reply2])
    _patch_graph(monkeypatch, model2, [dummy_tool])

    events = await _collect_events(ChatStreamRequest(username="alice", content="换个问题", conversationId="alice"))

    assert not any(e["type"] == "error" for e in events)
    assert events[-1]["type"] == "done"
    assert events[-1]["content"] == "没问题"


async def test_resume_with_stale_proposal_id_returns_error(monkeypatch, fake_java_client):
    from langgraph.types import interrupt

    @tool
    def add_timeline_item_stub(date: str, title: str) -> str:
        """stub"""
        decision = interrupt({"blockType": "timeline", "noteId": 9, "preview": {"date": date, "title": title}})
        return "已添加" if decision == "accept" else "用户拒绝了"

    from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
    tool_call_msg = AIMessage(content="", tool_calls=[
        {"name": "add_timeline_item_stub", "args": {"date": "2024-01", "title": "事件一"}, "id": "call-1"}
    ])
    model = FakeMessagesListChatModel(responses=[tool_call_msg])
    _patch_graph(monkeypatch, model, [add_timeline_item_stub])

    await _collect_events(ChatStreamRequest(username="alice", content="加一条", conversationId="alice"))

    from app.routes.chat import _resume_agent, ChatResumeRequest
    resume_lines = [line async for line in _resume_agent(
        ChatResumeRequest(conversationId="alice", proposalId="not-the-real-id", decision="accept")
    )]
    resume_events = [json.loads(line) for line in resume_lines]

    assert resume_events[0]["type"] == "error"


async def test_analyze_image_emits_media_block_updated_before_done(monkeypatch, fake_java_client):
    from tools.analyze_image import make_analyze_image_tool
    import tools.analyze_image as analyze_image_route
    from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel

    fake_java_client.list_media_blocks = lambda note_id: _async_return(
        [{"blockId": "b1", "blockType": "image", "url": "a.png"}]
    )
    fake_java_client.get_block_media = lambda note_id, block_id: _async_return(
        {"mimeType": "image/png", "base64Data": "abc"}
    )
    fake_java_client.patch_media_block = lambda note_id, block_id, fields: _async_return(
        {"url": "a.png", **fields}
    )
    monkeypatch.setattr(analyze_image_route, "resolve_chat_model", lambda provider: FakeVisionModel("一张图片描述"))

    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "analyze_image", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="已经描述好了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])

    def fake_builder(java_client, provider, citations_sink=None, block_update_sink=None, media_block_update_sink=None):
        analyze_tool = make_analyze_image_tool(java_client, media_block_update_sink)
        return build_graph_builder(model, [analyze_tool]), [analyze_tool]

    monkeypatch.setattr(chat_route, "build_default_graph_builder", fake_builder)

    events = await _collect_events(ChatStreamRequest(
        username="alice", content="看看这张图", conversationId="alice", currentNoteId=9
    ))

    event_types = [e["type"] for e in events]
    assert "media_block_updated" in event_types
    assert event_types.index("media_block_updated") < event_types.index("done")
    media_event = next(e for e in events if e["type"] == "media_block_updated")
    assert media_event["noteId"] == 9
    assert media_event["blockId"] == "b1"
    assert media_event["blockType"] == "image"
    assert media_event["data"] == {"url": "a.png", "caption": "一张图片描述"}


async def _async_return(value):
    return value


class FakeVisionModel:
    def __init__(self, content):
        self._content = content

    async def ainvoke(self, messages):
        return AIMessage(content=self._content)
