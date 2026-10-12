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
        "current_project_context": None,
        "current_project_id": None,
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


async def test_search_workspace_labels_each_chunk_with_source_id():
    chunks = [
        {"chunkText": "网关设计", "sourceType": "NOTE", "sourceId": 12},
        {"chunkText": "剪藏内容", "sourceType": "CLIP", "sourceId": 7},
        {"chunkText": "录音片段", "sourceType": "LOCAL_MEDIA", "sourceId": 3},
    ]
    tool_ = make_search_workspace_tool(FakeJavaClient(chunks), ContentCache())

    result = await tool_.ainvoke({"query": "hello", "state": _agent_state()})

    assert "[笔记 id=12] 网关设计" in result
    assert "[收藏 id=7] 剪藏内容" in result
    assert "- 录音片段" in result


from tools.read_document import make_read_clip_tool, make_read_note_tool


class FakeDocumentJavaClient:
    def __init__(self, title_matches=None, docs=None):
        self._title_matches = title_matches or []
        self._docs = docs or {}
        self.title_calls: list[tuple[str, str]] = []
        self.read_calls: list[tuple[str, str, int]] = []

    async def find_notes_by_title(self, username, title):
        self.title_calls.append((username, title))
        return self._title_matches

    async def read_note(self, username, note_id):
        self.read_calls.append((username, "note", note_id))
        return self._docs.get(("note", note_id))

    async def read_clip(self, username, clip_id):
        self.read_calls.append((username, "clip", clip_id))
        return self._docs.get(("clip", clip_id))


async def test_read_note_by_id_returns_full_body():
    java = FakeDocumentJavaClient(docs={("note", 12): {"id": 12, "title": "网关设计", "bodyText": "正文全文"}})
    tool_ = make_read_note_tool(java, ContentCache())

    result = await tool_.ainvoke({"note_id": 12, "state": _agent_state()})

    assert "【网关设计】" in result
    assert "正文全文" in result
    assert java.read_calls == [("alice", "note", 12)]


async def test_read_note_by_title_strips_book_brackets_and_reads_single_match():
    java = FakeDocumentJavaClient(
        title_matches=[{"type": "note", "id": 12, "title": "2026-10-05-sms-gateway-design"}],
        docs={("note", 12): {"id": 12, "title": "2026-10-05-sms-gateway-design", "bodyText": "架构"}},
    )
    tool_ = make_read_note_tool(java, ContentCache())

    result = await tool_.ainvoke({"title": "《2026-10-05-sms-gateway-design》", "state": _agent_state()})

    assert java.title_calls == [("alice", "2026-10-05-sms-gateway-design")]
    assert "架构" in result


async def test_read_note_by_title_prefers_exact_match_among_several():
    java = FakeDocumentJavaClient(
        title_matches=[
            {"type": "note", "id": 13, "title": "2026-10-05-sms-gateway-design"},
            {"type": "note", "id": 12, "title": "2026-10-05-sms-gateway"},
        ],
        docs={("note", 12): {"id": 12, "title": "2026-10-05-sms-gateway", "bodyText": "实现"}},
    )
    tool_ = make_read_note_tool(java, ContentCache())

    result = await tool_.ainvoke({"title": "2026-10-05-sms-gateway", "state": _agent_state()})

    assert "实现" in result
    assert java.read_calls == [("alice", "note", 12)]


async def test_read_note_by_title_lists_candidates_when_ambiguous():
    java = FakeDocumentJavaClient(title_matches=[
        {"type": "note", "id": 12, "title": "sms-gateway"},
        {"type": "note", "id": 13, "title": "sms-gateway-design"},
    ])
    tool_ = make_read_note_tool(java, ContentCache())

    result = await tool_.ainvoke({"title": "sms", "state": _agent_state()})

    assert "id=12" in result and "id=13" in result
    assert java.read_calls == []


async def test_read_note_not_found_or_not_owned():
    tool_ = make_read_note_tool(FakeDocumentJavaClient(), ContentCache())

    result = await tool_.ainvoke({"note_id": 99, "state": _agent_state()})

    assert "找不到" in result


async def test_read_note_requires_id_or_title():
    tool_ = make_read_note_tool(FakeDocumentJavaClient(), ContentCache())

    result = await tool_.ainvoke({"state": _agent_state()})

    assert "note_id 或 title" in result


async def test_read_note_offloads_very_long_body():
    body = "长" * 20000
    java = FakeDocumentJavaClient(docs={("note", 12): {"id": 12, "title": "长文", "bodyText": body}})
    cache = ContentCache()
    tool_ = make_read_note_tool(java, cache)

    result = await tool_.ainvoke({"note_id": 12, "state": _agent_state()})

    assert "cache_id=" in result
    assert body not in result


async def test_read_note_returns_friendly_message_on_java_error_instead_of_raising():
    class RaisingJavaClient:
        async def read_note(self, username, note_id):
            raise RuntimeError("Java service unreachable")

    tool_ = make_read_note_tool(RaisingJavaClient(), ContentCache())

    result = await tool_.ainvoke({"note_id": 12, "state": _agent_state()})

    assert "读取笔记失败" in result


async def test_read_clip_returns_full_body():
    java = FakeDocumentJavaClient(docs={("clip", 7): {"id": 7, "title": "剪藏", "bodyText": "原文"}})
    tool_ = make_read_clip_tool(java, ContentCache())

    result = await tool_.ainvoke({"clip_id": 7, "state": _agent_state()})

    assert "原文" in result
    assert java.read_calls == [("alice", "clip", 7)]


async def test_read_note_mentions_images_when_document_has_them():
    java = FakeDocumentJavaClient(docs={("note", 12): {"id": 12, "title": "设计", "bodyText": "正文", "imageCount": 3}})
    tool_ = make_read_note_tool(java, ContentCache())

    result = await tool_.ainvoke({"note_id": 12, "state": _agent_state()})

    assert "3张图片" in result
    assert "view_image(note_id=12" in result


from tools.list_clips import make_list_clips_tool


class FakeClipListJavaClient:
    def __init__(self, result=None, error: Exception | None = None):
        self._result = result
        self._error = error
        self.calls: list[tuple] = []

    async def list_clips(self, username, domain=None, tag=None, keyword=None, limit=None):
        self.calls.append((username, domain, tag, keyword, limit))
        if self._error:
            raise self._error
        return self._result


async def test_list_clips_passes_filters_and_reports_truncation():
    java = FakeClipListJavaClient({"total": 120, "items": [
        {"id": 7, "title": "vuejs/core", "url": "https://github.com/vuejs/core", "tags": ["开源"],
         "savedAt": "2019-03-01T00:00:00"},
    ]})
    tool_ = make_list_clips_tool(java)

    result = await tool_.ainvoke({"domain": "github.com", "limit": 1, "state": _agent_state()})

    assert java.calls == [("alice", "github.com", None, None, 1)]
    assert "共 120 条" in result
    assert "最新的 1 条" in result
    assert "- vuejs/core — https://github.com/vuejs/core（收藏 id=7，标签：开源，收藏于 2019-03-01）" in result


async def test_list_clips_says_so_when_nothing_matches():
    tool_ = make_list_clips_tool(FakeClipListJavaClient({"total": 0, "items": []}))

    result = await tool_.ainvoke({"tag": "不存在", "state": _agent_state()})

    assert result == "没有符合条件的收藏。"


async def test_list_clips_returns_error_text_instead_of_raising():
    tool_ = make_list_clips_tool(FakeClipListJavaClient(error=RuntimeError("boom")))

    result = await tool_.ainvoke({"domain": "github.com", "state": _agent_state()})

    assert "列出收藏失败" in result
