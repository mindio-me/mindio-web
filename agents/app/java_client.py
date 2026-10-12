# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""
调用Java后端 /internal/** 接口的客户端：检索(RetrievalService)和agent状态
(LangGraph checkpointer的持久化后端)。每个请求都带共享密钥请求头，
详见 docs/superpowers/specs/2026-09-04-agent-service-langgraph-design.md。
"""
from __future__ import annotations

import logging

import httpx

from app.config import Settings, settings as default_settings

logger = logging.getLogger(__name__)

INTERNAL_TOKEN_HEADER = "X-Internal-Token"


class JavaClientError(Exception):
    """Java内部API调用失败（网络错误、非2xx响应等），供上层区分处理。"""


class JavaClient:
    def __init__(self, cfg: Settings = default_settings, client: httpx.AsyncClient | None = None):
        self._cfg = cfg
        self._client = client or httpx.AsyncClient(
            base_url=cfg.java_internal_base_url,
            headers={INTERNAL_TOKEN_HEADER: cfg.internal_shared_token},
            timeout=30.0,
        )

    async def retrieve(self, username: str, query: str, top_k: int = 5) -> list[dict]:
        logger.info("retrieve request: username=%r query=%r top_k=%r", username, query, top_k)
        try:
            resp = await self._client.post(
                "/internal/retrieve",
                json={"username": username, "query": query, "topK": top_k},
            )
            logger.info("retrieve response: status=%r body=%r", resp.status_code, resp.text)
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            logger.exception("retrieve failed for query=%r", query)
            raise JavaClientError(f"retrieve failed for query={query!r}: {e}") from e

    async def search_web(self, query: str, limit: int = 5) -> list[dict]:
        try:
            resp = await self._client.post(
                "/internal/search-web",
                json={"query": query, "limit": limit},
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(f"search_web failed for query={query!r}: {e}") from e

    async def get_note_references(self, note_id: int) -> list[dict]:
        try:
            resp = await self._client.get(f"/internal/note-references/{note_id}")
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(f"get_note_references failed for note_id={note_id!r}: {e}") from e

    async def append_block_item(self, note_id: int, block_type: str, item: dict) -> list[dict]:
        try:
            resp = await self._client.post(
                f"/internal/notes/{note_id}/topic-blocks/{block_type}/items",
                json=item,
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(
                f"append_block_item failed for note_id={note_id!r} block_type={block_type!r}: {e}"
            ) from e

    async def list_media_blocks(self, note_id: int) -> list[dict]:
        try:
            resp = await self._client.get(f"/internal/notes/{note_id}/media-blocks")
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(f"list_media_blocks failed for note_id={note_id!r}: {e}") from e

    async def trigger_audio_transcription(self, note_id: int, block_id: str) -> dict:
        try:
            resp = await self._client.post(f"/internal/notes/{note_id}/audio-transcriptions/{block_id}")
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(
                f"trigger_audio_transcription failed for note_id={note_id!r} block_id={block_id!r}: {e}"
            ) from e

    async def get_block_media(self, note_id: int, block_id: str) -> dict:
        try:
            resp = await self._client.get(f"/internal/notes/{note_id}/blocks/{block_id}/content")
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(
                f"get_block_media failed for note_id={note_id!r} block_id={block_id!r}: {e}"
            ) from e

    async def get_block_audio(self, note_id: int, block_id: str, segment: int | None = None) -> bytes:
        """音频块的原始字节：整段，或录音时切好的第 segment 段。长录音可能上百 MB，
        所以不走 get_block_media 那种 base64 JSON，读超时也放宽。"""
        params = {"segment": segment} if segment is not None else None
        try:
            resp = await self._client.get(
                f"/internal/notes/{note_id}/blocks/{block_id}/audio", params=params, timeout=300.0,
            )
            resp.raise_for_status()
            return resp.content
        except httpx.HTTPError as e:
            raise JavaClientError(
                f"get_block_audio failed for note_id={note_id!r} block_id={block_id!r} segment={segment!r}: {e}"
            ) from e

    async def patch_media_block(self, note_id: int, block_id: str, fields: dict) -> dict:
        try:
            resp = await self._client.patch(
                f"/internal/notes/{note_id}/blocks/{block_id}",
                json=fields,
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(
                f"patch_media_block failed for note_id={note_id!r} block_id={block_id!r}: {e}"
            ) from e

    async def get_ai_config(self) -> dict:
        try:
            resp = await self._client.get("/internal/ai-config")
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(f"get_ai_config failed: {e}") from e

    async def get_state(self, conversation_id: str) -> str | None:
        try:
            resp = await self._client.get(f"/internal/agent-state/{conversation_id}")
            resp.raise_for_status()
            return resp.json().get("stateBlob")
        except httpx.HTTPError as e:
            raise JavaClientError(f"get_state failed for conversation_id={conversation_id!r}: {e}") from e

    async def put_state(self, conversation_id: str, state_blob: str) -> None:
        try:
            resp = await self._client.put(
                f"/internal/agent-state/{conversation_id}",
                json={"stateBlob": state_blob},
            )
            resp.raise_for_status()
        except httpx.HTTPError as e:
            raise JavaClientError(f"put_state failed for conversation_id={conversation_id!r}: {e}") from e

    async def get_project_notes(self, project_id: int) -> list[dict]:
        try:
            resp = await self._client.get(f"/internal/projects/{project_id}/notes")
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(f"get_project_notes failed for project_id={project_id!r}: {e}") from e

    async def find_notes_by_title(self, username: str, title: str) -> list[dict]:
        try:
            resp = await self._client.get(
                "/internal/documents/notes", params={"username": username, "title": title},
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(f"find_notes_by_title failed for title={title!r}: {e}") from e

    async def list_clips(
        self, username: str, domain: str | None = None, tag: str | None = None,
        keyword: str | None = None, limit: int | None = None,
    ) -> dict:
        """返回 {total, items:[{id,title,url,tags,savedAt}]}；未传的筛选条件不发给 Java。"""
        params = {"username": username, "domain": domain, "tag": tag, "keyword": keyword, "limit": limit}
        try:
            resp = await self._client.get(
                "/internal/documents/clips", params={k: v for k, v in params.items() if v is not None},
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(f"list_clips failed for domain={domain!r} tag={tag!r} keyword={keyword!r}: {e}") from e

    async def read_note(self, username: str, note_id: int) -> dict | None:
        """不存在或不属于该用户时返回 None（Java 侧两种情况统一回 404，不区分）。"""
        return await self._read_document(username, "notes", note_id)

    async def read_clip(self, username: str, clip_id: int) -> dict | None:
        return await self._read_document(username, "clips", clip_id)

    async def list_document_images(self, username: str, kind: str, doc_id: int) -> list[dict] | None:
        """kind 是 "notes" 或 "clips"；文档不存在或不属于该用户时返回 None。"""
        try:
            resp = await self._client.get(f"/internal/documents/{kind}/{doc_id}/images", params={"username": username})
            if resp.status_code == 404:
                return None
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(f"list images failed for {kind} id={doc_id!r}: {e}") from e

    async def get_document_image(self, username: str, kind: str, doc_id: int, index: int) -> dict:
        """本地图片返回 {mimeType, base64Data}，外链图片返回 {url}。"""
        try:
            resp = await self._client.get(
                f"/internal/documents/{kind}/{doc_id}/images/{index}", params={"username": username},
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(f"get image failed for {kind} id={doc_id!r} index={index!r}: {e}") from e

    async def insert_note_image(
        self, username: str, note_id: int, source: dict, caption: str, anchor_text: str | None,
        conversation_id: str | None = None,
    ) -> dict:
        return await self._write_document(
            "POST", f"/internal/documents/notes/{note_id}/images", username,
            {"source": source, "caption": caption, "anchorText": anchor_text},
            params=_conversation(conversation_id),
        )

    async def delete_note_image(
        self, username: str, note_id: int, index: int, conversation_id: str | None = None,
    ) -> dict:
        return await self._write_document(
            "DELETE", f"/internal/documents/notes/{note_id}/images/{index}", username,
            params=_conversation(conversation_id),
        )

    async def set_note_image_caption(
        self, username: str, note_id: int, index: int, caption: str, conversation_id: str | None = None,
    ) -> dict:
        return await self._write_document(
            "PATCH", f"/internal/documents/notes/{note_id}/images/{index}", username, {"caption": caption},
            params=_conversation(conversation_id),
        )

    async def _write_document(
        self, method: str, path: str, username: str, body: dict | None = None, params: dict | None = None,
    ) -> dict:
        """4xx/5xx 时把 Java 返回的 message 原样放进异常——"找不到包含「…」的段落"这类原因
        要让模型看到，它才能换锚点重试。网上图片要下载，超时放宽到 60 秒。"""
        try:
            resp = await self._client.request(
                method, path, params={"username": username, **(params or {})}, json=body, timeout=60.0,
            )
        except httpx.HTTPError as e:
            raise JavaClientError(f"{method} {path} failed: {e}") from e
        if resp.status_code >= 400:
            raise JavaClientError(_error_message(resp))
        return resp.json()

    async def get_note_blocks(self, username: str, note_id: int) -> dict | None:
        try:
            resp = await self._client.get(f"/internal/documents/notes/{note_id}/blocks", params={"username": username})
        except httpx.HTTPError as e:
            raise JavaClientError(f"get_note_blocks failed for note_id={note_id!r}: {e}") from e
        if resp.status_code == 404:
            return None
        if resp.status_code >= 400:
            raise JavaClientError(_error_message(resp))
        return resp.json()

    async def edit_note(self, username: str, note_id: int, conversation_id: str | None, operations: list[dict]) -> dict:
        return await self._write_document(
            "POST", f"/internal/documents/notes/{note_id}/edits", username,
            {"operations": operations}, params=_conversation(conversation_id),
        )

    async def _read_document(self, username: str, kind: str, doc_id: int) -> dict | None:
        try:
            resp = await self._client.get(f"/internal/documents/{kind}/{doc_id}", params={"username": username})
            if resp.status_code == 404:
                return None
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(f"read {kind} failed for id={doc_id!r}: {e}") from e

    async def patch_project_field(self, project_id: int, field: str, value: str) -> dict:
        try:
            resp = await self._client.patch(
                f"/internal/projects/{project_id}/fields",
                json={"field": field, "value": value},
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(
                f"patch_project_field failed for project_id={project_id!r} field={field!r}: {e}"
            ) from e

    async def aclose(self) -> None:
        await self._client.aclose()


def _error_message(resp: httpx.Response) -> str:
    try:
        body = resp.json()
    except ValueError:
        body = None
    message = body.get("message") if isinstance(body, dict) else None
    return message or f"HTTP {resp.status_code}"


def _conversation(conversation_id: str | None) -> dict:
    return {"conversationId": conversation_id} if conversation_id else {}
