# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only
import json

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


async def test_get_block_audio_returns_raw_bytes_for_whole_file(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/notes/9/blocks/r1/audio"
        assert "segment" not in request.url.params
        return httpx.Response(200, content=b"RAW-webm-bytes", headers={"Content-Type": "audio/webm"})

    client = _client_with_transport(test_settings, handler)
    assert await client.get_block_audio(9, "r1") == b"RAW-webm-bytes"


async def test_get_block_audio_requests_one_segment(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.params["segment"] == "2"
        return httpx.Response(200, content=b"seg2")

    client = _client_with_transport(test_settings, handler)
    assert await client.get_block_audio(9, "r1", segment=2) == b"seg2"


async def test_get_block_audio_wraps_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(404, json={"error": "not found"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.get_block_audio(9, "r1", segment=0)


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


async def test_get_project_notes_sends_expected_request_and_parses_response(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["method"] = request.method
        return httpx.Response(200, json=[{"id": 1, "title": "开发日志", "bodyText": "正文"}])

    client = _client_with_transport(test_settings, handler)
    result = await client.get_project_notes(9)

    assert captured["method"] == "GET"
    assert captured["url"] == "http://java.internal.test/internal/projects/9/notes"
    assert result == [{"id": 1, "title": "开发日志", "bodyText": "正文"}]


async def test_get_project_notes_wraps_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, json={"error": "boom"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.get_project_notes(9)


async def test_patch_project_field_sends_expected_request_and_parses_response(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["method"] = request.method
        captured["body"] = request.content
        return httpx.Response(200, json={"id": 9, "descriptionZh": "新简介"})

    client = _client_with_transport(test_settings, handler)
    result = await client.patch_project_field(9, "descriptionZh", "新简介")

    assert captured["method"] == "PATCH"
    assert captured["url"] == "http://java.internal.test/internal/projects/9/fields"
    assert b'"field":"descriptionZh"' in captured["body"]
    assert result == {"id": 9, "descriptionZh": "新简介"}


async def test_patch_project_field_wraps_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(400, json={"error": "bad field"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.patch_project_field(9, "projectUrl", "x")


async def test_get_ai_config_calls_internal_endpoint(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        assert str(request.url) == "http://java.internal.test/internal/ai-config"
        assert request.headers["x-internal-token"] == "test-token"
        return httpx.Response(200, json={"defaultProvider": "doubao"})

    client = _client_with_transport(test_settings, handler)
    assert await client.get_ai_config() == {"defaultProvider": "doubao"}


async def test_get_ai_config_wraps_http_errors(test_settings):
    client = _client_with_transport(test_settings, lambda request: httpx.Response(500))
    with pytest.raises(JavaClientError):
        await client.get_ai_config()


async def test_trigger_audio_transcription_posts_to_internal_endpoint(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"], captured["path"] = request.method, request.url.path
        return httpx.Response(200, json={"blockId": "b1", "status": "PENDING"})

    client = _client_with_transport(test_settings, handler)
    result = await client.trigger_audio_transcription(3, "b1")

    assert captured == {"method": "POST", "path": "/internal/notes/3/audio-transcriptions/b1"}
    assert result["status"] == "PENDING"


async def test_find_notes_by_title_sends_expected_request(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = request.url
        return httpx.Response(200, json=[{"type": "note", "id": 1, "title": "设计"}])

    client = _client_with_transport(test_settings, handler)
    result = await client.find_notes_by_title("alice", "设计")

    assert captured["url"].path == "/internal/documents/notes"
    assert dict(captured["url"].params) == {"username": "alice", "title": "设计"}
    assert result == [{"type": "note", "id": 1, "title": "设计"}]


async def test_read_note_sends_expected_request(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = request.url
        return httpx.Response(200, json={"type": "note", "id": 5, "title": "t", "bodyText": "b"})

    client = _client_with_transport(test_settings, handler)
    result = await client.read_note("alice", 5)

    assert captured["url"].path == "/internal/documents/notes/5"
    assert dict(captured["url"].params) == {"username": "alice"}
    assert result["bodyText"] == "b"


async def test_read_clip_returns_none_on_404(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/documents/clips/5"
        return httpx.Response(404, json={"error": "收藏不存在"})

    client = _client_with_transport(test_settings, handler)
    assert await client.read_clip("alice", 5) is None


async def test_read_note_wraps_other_http_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, json={"error": "boom"})

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(JavaClientError):
        await client.read_note("alice", 5)


async def test_list_document_images_sends_expected_request(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = request.url
        return httpx.Response(200, json=[{"index": 1, "url": "/uploads/a.png", "caption": ""}])

    client = _client_with_transport(test_settings, handler)
    result = await client.list_document_images("alice", "clips", 7)

    assert captured["url"].path == "/internal/documents/clips/7/images"
    assert dict(captured["url"].params) == {"username": "alice"}
    assert result[0]["index"] == 1


async def test_list_document_images_returns_none_on_404(test_settings):
    client = _client_with_transport(test_settings, lambda request: httpx.Response(404, json={}))
    assert await client.list_document_images("alice", "notes", 7) is None


async def test_get_document_image_sends_expected_request(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = request.url
        return httpx.Response(200, json={"url": "https://example.com/a.png"})

    client = _client_with_transport(test_settings, handler)
    result = await client.get_document_image("alice", "notes", 7, 2)

    assert captured["url"].path == "/internal/documents/notes/7/images/2"
    assert result == {"url": "https://example.com/a.png"}


async def test_get_document_image_wraps_http_errors(test_settings):
    client = _client_with_transport(test_settings, lambda request: httpx.Response(400, json={}))
    with pytest.raises(JavaClientError):
        await client.get_document_image("alice", "notes", 7, 9)


async def test_insert_note_image_posts_source_and_returns_event(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"], captured["url"] = request.method, request.url
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"op": "insert", "noteId": 9})

    client = _client_with_transport(test_settings, handler)
    result = await client.insert_note_image("alice", 9, {"type": "url", "url": "https://x/a.png"}, "图", None)

    assert captured["method"] == "POST"
    assert captured["url"].path == "/internal/documents/notes/9/images"
    assert dict(captured["url"].params) == {"username": "alice"}
    assert captured["body"] == {"source": {"type": "url", "url": "https://x/a.png"}, "caption": "图", "anchorText": None}
    assert result == {"op": "insert", "noteId": 9}


async def test_delete_and_caption_use_expected_methods(test_settings):
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append((request.method, request.url.path, request.content))
        return httpx.Response(200, json={"op": "x"})

    client = _client_with_transport(test_settings, handler)
    await client.delete_note_image("alice", 9, 2)
    await client.set_note_image_caption("alice", 9, 1, "新")

    assert seen[0][:2] == ("DELETE", "/internal/documents/notes/9/images/2")
    assert seen[1][:2] == ("PATCH", "/internal/documents/notes/9/images/1")
    assert json.loads(seen[1][2]) == {"caption": "新"}


async def test_write_errors_surface_java_message(test_settings):
    client = _client_with_transport(test_settings, lambda request: httpx.Response(
        400, json={"status": 400, "error": "Bad Request", "message": "找不到包含「xx」的段落"}))

    with pytest.raises(JavaClientError, match="找不到包含「xx」的段落"):
        await client.delete_note_image("alice", 9, 1)


async def test_get_note_blocks_and_404(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/9/blocks"):
            assert dict(request.url.params) == {"username": "alice"}
            return httpx.Response(200, json={"blocks": []})
        return httpx.Response(404, json={})

    client = _client_with_transport(test_settings, handler)
    assert await client.get_note_blocks("alice", 9) == {"blocks": []}
    assert await client.get_note_blocks("alice", 8) is None


async def test_edit_note_posts_operations_with_conversation(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"], captured["body"] = request.url, json.loads(request.content)
        return httpx.Response(200, json={"ops": []})

    client = _client_with_transport(test_settings, handler)
    await client.edit_note("alice", 9, "5", [{"op": "insert", "after": "end", "markdown": "x"}])

    assert captured["url"].path == "/internal/documents/notes/9/edits"
    assert dict(captured["url"].params) == {"username": "alice", "conversationId": "5"}
    assert captured["body"] == {"operations": [{"op": "insert", "after": "end", "markdown": "x"}]}


async def test_image_writes_pass_conversation_id(test_settings):
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(dict(request.url.params))
        return httpx.Response(200, json={})

    client = _client_with_transport(test_settings, handler)
    await client.delete_note_image("alice", 9, 1, conversation_id="5")

    assert seen == [{"username": "alice", "conversationId": "5"}]


async def test_list_clips_sends_only_given_filters(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = request.url
        return httpx.Response(200, json={"total": 0, "items": []})

    client = _client_with_transport(test_settings, handler)
    result = await client.list_clips("alice", domain="github.com", limit=50)

    assert captured["url"].path == "/internal/documents/clips"
    assert dict(captured["url"].params) == {"username": "alice", "domain": "github.com", "limit": "50"}
    assert result == {"total": 0, "items": []}


async def test_create_note_posts_title(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"], captured["url"] = request.method, request.url
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"noteId": 31, "title": "English"})

    client = _client_with_transport(test_settings, handler)
    result = await client.create_note("alice", "English")

    assert captured["method"] == "POST"
    assert captured["url"].path == "/internal/documents/notes"
    assert dict(captured["url"].params) == {"username": "alice"}
    assert captured["body"] == {"title": "English"}
    assert result == {"noteId": 31, "title": "English"}


async def test_edit_note_snapshot_false_adds_query_param(test_settings):
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(dict(request.url.params))
        return httpx.Response(200, json={"ops": []})

    client = _client_with_transport(test_settings, handler)
    await client.edit_note("alice", 31, "5", [{"op": "insert", "after": "end", "markdown": "x"}], snapshot=False)
    await client.edit_note("alice", 9, "5", [{"op": "insert", "after": "end", "markdown": "x"}])

    assert seen[0] == {"username": "alice", "conversationId": "5", "snapshot": "false"}
    assert "snapshot" not in seen[1]

