# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

import httpx
import pytest

from app.java_client import JavaClient, JavaClientError


def _client_with_transport(cfg, handler) -> JavaClient:
    transport = httpx.MockTransport(handler)
    async_client = httpx.AsyncClient(
        base_url=cfg.java_internal_base_url,
        headers={"X-Internal-Token": cfg.internal_shared_token},
        transport=transport,
    )
    return JavaClient(cfg=cfg, client=async_client)


async def test_retrieve_sends_expected_request_and_parses_response(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["headers"] = dict(request.headers)
        captured["body"] = request.content
        return httpx.Response(200, json=[{"sourceType": "NOTE", "sourceId": 1, "chunkText": "hi", "score": 0.9}])

    client = _client_with_transport(test_settings, handler)
    result = await client.retrieve("alice", "hello world", top_k=5)

    assert captured["url"] == "http://java.internal.test/internal/retrieve"
    assert captured["headers"]["x-internal-token"] == "test-token"
    assert b'"username":"alice"' in captured["body"]
    assert b'"topK":5' in captured["body"]
    assert result == [{"sourceType": "NOTE", "sourceId": 1, "chunkText": "hi", "score": 0.9}]


async def test_retrieve_wraps_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, json={"error": "boom"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.retrieve("alice", "q")


async def test_get_state_returns_none_when_no_blob(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/agent-state/alice"
        return httpx.Response(200, json={"conversationId": "alice", "stateBlob": None, "updatedAt": None})

    client = _client_with_transport(test_settings, handler)
    result = await client.get_state("alice")

    assert result is None


async def test_put_state_sends_blob(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["body"] = request.content
        return httpx.Response(200)

    client = _client_with_transport(test_settings, handler)
    await client.put_state("alice", '{"todos":[]}')

    assert captured["method"] == "PUT"
    assert b'"stateBlob"' in captured["body"]
    assert b'\\"todos\\":[]' in captured["body"]


async def test_search_web_sends_expected_request_and_parses_response(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["body"] = request.content
        return httpx.Response(200, json=[{"title": "标题", "url": "https://x.com", "excerpt": "摘要"}])

    client = _client_with_transport(test_settings, handler)
    result = await client.search_web("用户增长", limit=5)

    assert captured["url"] == "http://java.internal.test/internal/search-web"
    assert b'"query":"' in captured["body"]
    assert b'"limit":5' in captured["body"]
    assert result == [{"title": "标题", "url": "https://x.com", "excerpt": "摘要"}]


async def test_search_web_wraps_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, json={"error": "boom"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.search_web("q")


async def test_get_note_references_sends_expected_request_and_parses_response(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/note-references/9"
        return httpx.Response(200, json=[{"title": "参考文章", "content": "正文"}])

    client = _client_with_transport(test_settings, handler)
    result = await client.get_note_references(9)

    assert result == [{"title": "参考文章", "content": "正文"}]


async def test_get_note_references_wraps_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, json={"error": "boom"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.get_note_references(9)


async def test_append_block_item_sends_expected_request_and_parses_response(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["body"] = request.content
        return httpx.Response(200, json=[{"date": "2024-01", "title": "事件一"}])

    client = _client_with_transport(test_settings, handler)
    result = await client.append_block_item(9, "timeline", {"date": "2024-01", "title": "事件一"})

    assert captured["url"] == "http://java.internal.test/internal/notes/9/topic-blocks/timeline/items"
    assert b'"title":"\\u4e8b\\u4ef6\\u4e00"' in captured["body"] or "事件一".encode() in captured["body"]
    assert result == [{"date": "2024-01", "title": "事件一"}]


async def test_append_block_item_wraps_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, json={"error": "boom"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.append_block_item(9, "timeline", {"date": "2024-01"})


async def test_list_media_blocks_sends_expected_request_and_parses_response(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/notes/9/media-blocks"
        return httpx.Response(200, json=[{"blockId": "b1", "blockType": "image", "url": "a.png"}])

    client = _client_with_transport(test_settings, handler)
    result = await client.list_media_blocks(9)

    assert result == [{"blockId": "b1", "blockType": "image", "url": "a.png"}]


async def test_list_media_blocks_wraps_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, json={"error": "boom"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.list_media_blocks(9)


async def test_get_block_media_sends_expected_request_and_parses_response(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/notes/9/blocks/b1/content"
        return httpx.Response(200, json={"mimeType": "image/png", "base64Data": "abc"})

    client = _client_with_transport(test_settings, handler)
    result = await client.get_block_media(9, "b1")

    assert result == {"mimeType": "image/png", "base64Data": "abc"}


async def test_get_block_media_wraps_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(404, json={"error": "not found"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.get_block_media(9, "missing")


async def test_patch_media_block_sends_expected_request_and_parses_response(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["url"] = str(request.url)
        captured["body"] = request.content
        return httpx.Response(200, json={"url": "a.png", "caption": "一张图片描述"})

    client = _client_with_transport(test_settings, handler)
    result = await client.patch_media_block(9, "b1", {"caption": "一张图片描述"})

    assert captured["method"] == "PATCH"
    assert captured["url"] == "http://java.internal.test/internal/notes/9/blocks/b1"
    assert result == {"url": "a.png", "caption": "一张图片描述"}


async def test_patch_media_block_wraps_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(400, json={"error": "boom"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.patch_media_block(9, "b1", {"caption": "x"})
