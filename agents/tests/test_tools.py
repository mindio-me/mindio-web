# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

from langgraph.types import Command

from tools.content_cache import ContentCache
from tools.read_cached_content import make_read_cached_content_tool
from tools.search_workspace import make_search_workspace_tool
from tools.update_todo_list import update_todo_list


class FakeJavaClient:
    def __init__(
        self,
        chunks: list[dict],
        web_results: list[dict] | None = None,
        note_references: list[dict] | None = None,
    ):
        self._chunks = chunks
        self._web_results = web_results or []
        self._note_references = note_references or []
        self.calls: list[tuple[str, str, int]] = []
        self.web_calls: list[tuple[str, int]] = []
        self.note_reference_calls: list[int] = []

    async def retrieve(self, username: str, query: str, top_k: int) -> list[dict]:
        self.calls.append((username, query, top_k))
        return self._chunks

    async def search_web(self, query: str, limit: int) -> list[dict]:
        self.web_calls.append((query, limit))
        return self._web_results

    async def get_note_references(self, note_id: int) -> list[dict]:
        self.note_reference_calls.append(note_id)
        return self._note_references


def _agent_state(username: str = "alice", current_note_id: int | None = None) -> dict:
    return {
        "messages": [],
        "todos": [],
        "conversation_id": username,
        "username": username,
        "current_note_context": None,
        "current_note_id": current_note_id,
    }


async def test_search_workspace_returns_text_directly_when_short():
    java = FakeJavaClient([{"chunkText": "short note content"}])
    cache = ContentCache()
    tool_ = make_search_workspace_tool(java, cache)

    result = await tool_.ainvoke({"query": "hello", "state": _agent_state()})

    assert "short note content" in result
    assert java.calls == [("alice", "hello", 5)]


async def test_search_workspace_offloads_when_result_is_long():
    long_chunk = "x" * 5000
    java = FakeJavaClient([{"chunkText": long_chunk}])
    cache = ContentCache()
    tool_ = make_search_workspace_tool(java, cache)

    result = await tool_.ainvoke({"query": "hello", "state": _agent_state()})

    assert "cache_id=" in result
    assert "read_cached_content" in result
    assert long_chunk not in result  # 全文不应该直接出现在返回值里


async def test_search_workspace_no_results():
    java = FakeJavaClient([])
    cache = ContentCache()
    tool_ = make_search_workspace_tool(java, cache)

    result = await tool_.ainvoke({"query": "hello", "state": _agent_state()})

    assert "没有搜索到" in result


async def test_search_workspace_returns_friendly_message_on_java_error_instead_of_raising():
    """复现真实报的bug：search_workspace内部异常（Java 5xx/embedding服务挂了）如果
    原样往上抛，LangGraph这个版本的ToolNode默认不会兜底普通异常（只兜底它自己的
    ToolInvocationError），会直接打断整轮执行，把这条会话的消息历史留在"工具调用了
    但没有对应结果"的破损状态——下一次这条会话里发任何消息都会被provider拒绝
    （'insufficient tool messages following tool_calls message'）。"""
    class RaisingJavaClient:
        async def retrieve(self, username, query, top_k):
            raise RuntimeError("Doubao embedding API error: HTTP 404")

    tool_ = make_search_workspace_tool(RaisingJavaClient(), ContentCache())

    result = await tool_.ainvoke({"query": "hello", "state": _agent_state()})

    assert "搜索失败" in result
    assert "404" in result


async def test_search_workspace_records_citations_when_sink_provided():
    chunks = [{"chunkText": "note content", "sourceType": "NOTE", "sourceId": 1}]
    java = FakeJavaClient(chunks)
    cache = ContentCache()
    citations: list = []
    tool_ = make_search_workspace_tool(java, cache, citations_sink=citations)

    await tool_.ainvoke({"query": "hello", "state": _agent_state()})

    assert citations == chunks


def test_read_cached_content_returns_stored_value():
    cache = ContentCache()
    cache_id = cache.put("full cached text")
    tool_ = make_read_cached_content_tool(cache)

    result = tool_.invoke({"cache_id": cache_id})

    assert result == "full cached text"


def test_read_cached_content_unknown_id_returns_friendly_message():
    cache = ContentCache()
    tool_ = make_read_cached_content_tool(cache)

    result = tool_.invoke({"cache_id": "does-not-exist"})

    assert "没有找到" in result


def test_read_cached_content_respects_offset():
    cache = ContentCache()
    cache_id = cache.put("0123456789")
    tool_ = make_read_cached_content_tool(cache)

    result = tool_.invoke({"cache_id": cache_id, "offset": 5})

    assert result == "56789"


def test_update_todo_list_returns_command_updating_state_and_tool_message():
    todos = [{"id": "1", "task": "找资料", "status": "pending"}]

    result = update_todo_list.invoke({
        "name": "update_todo_list",
        "args": {"todos": todos},
        "id": "call-123",
        "type": "tool_call",
    })

    assert isinstance(result, Command)
    assert result.update["todos"] == todos
    messages = result.update["messages"]
    assert len(messages) == 1
    assert messages[0].tool_call_id == "call-123"


from tools.search_web import make_search_web_tool


async def test_search_web_returns_formatted_results():
    java = FakeJavaClient([], web_results=[{"title": "标题", "url": "https://x.com", "excerpt": "摘要"}])
    tool_ = make_search_web_tool(java)

    result = await tool_.ainvoke({"query": "用户增长"})

    assert "标题" in result
    assert "https://x.com" in result
    assert java.web_calls == [("用户增长", 5)]


async def test_search_web_no_results():
    java = FakeJavaClient([], web_results=[])
    tool_ = make_search_web_tool(java)

    result = await tool_.ainvoke({"query": "用户增长"})

    assert "没有搜索到" in result


async def test_search_web_returns_friendly_message_on_java_error_instead_of_raising():
    class RaisingJavaClient:
        async def search_web(self, query, limit):
            raise RuntimeError("search provider unavailable")

    tool_ = make_search_web_tool(RaisingJavaClient())

    result = await tool_.ainvoke({"query": "用户增长"})

    assert "联网搜索失败" in result


async def test_search_web_records_web_citations_when_sink_provided():
    java = FakeJavaClient([], web_results=[{"title": "标题", "url": "https://x.com", "excerpt": "摘要"}])
    citations: list = []
    tool_ = make_search_web_tool(java, citations_sink=citations)

    await tool_.ainvoke({"query": "用户增长"})

    assert citations == [{"sourceType": "WEB", "sourceUrl": "https://x.com", "title": "标题"}]


from tools.read_note_references import make_read_note_references_tool


async def test_read_note_references_returns_text_directly_when_short():
    java = FakeJavaClient([], note_references=[{"title": "参考文章", "content": "简短正文"}])
    cache = ContentCache()
    tool_ = make_read_note_references_tool(java, cache)

    result = await tool_.ainvoke({"state": _agent_state(current_note_id=9)})

    assert "参考文章" in result
    assert "简短正文" in result
    assert java.note_reference_calls == [9]


async def test_read_note_references_offloads_when_content_is_long():
    long_content = "x" * 5000
    java = FakeJavaClient([], note_references=[{"title": "长文章", "content": long_content}])
    cache = ContentCache()
    tool_ = make_read_note_references_tool(java, cache)

    result = await tool_.ainvoke({"state": _agent_state(current_note_id=9)})

    assert "cache_id=" in result
    assert long_content not in result


async def test_read_note_references_returns_friendly_message_on_java_error_instead_of_raising():
    class RaisingJavaClient:
        async def get_note_references(self, note_id):
            raise RuntimeError("Java service unreachable")

    cache = ContentCache()
    tool_ = make_read_note_references_tool(RaisingJavaClient(), cache)

    result = await tool_.ainvoke({"state": _agent_state(current_note_id=9)})

    assert "读取关联素材失败" in result


async def test_read_note_references_no_references():
    java = FakeJavaClient([], note_references=[])
    cache = ContentCache()
    tool_ = make_read_note_references_tool(java, cache)

    result = await tool_.ainvoke({"state": _agent_state(current_note_id=9)})

    assert "还没有关联任何素材" in result
