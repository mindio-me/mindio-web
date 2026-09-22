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

    async def get_block_media(self, note_id: int, block_id: str) -> dict:
        try:
            resp = await self._client.get(f"/internal/notes/{note_id}/blocks/{block_id}/content")
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPError as e:
            raise JavaClientError(
                f"get_block_media failed for note_id={note_id!r} block_id={block_id!r}: {e}"
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

    async def aclose(self) -> None:
        await self._client.aclose()
