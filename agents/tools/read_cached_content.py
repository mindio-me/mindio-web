# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""读取被search_workspace卸载到缓存里的完整内容，支持offset分段读取——避免一次性
读回的内容又把上下文撑爆（借鉴"友人建议"里read_file_segment的思路）。"""
from __future__ import annotations

from langchain_core.tools import tool

from tools.content_cache import ContentCache

SEGMENT_CHARS = 4000


def make_read_cached_content_tool(cache: ContentCache):
    @tool
    def read_cached_content(cache_id: str, offset: int = 0) -> str:
        """读取之前search_workspace因为内容过长而缓存起来的完整片段，用cache_id定位，
        offset用于分段读取过长的内容（每次最多返回一段，需要继续读后面的内容时增大offset）。"""
        content = cache.get(cache_id, offset=offset, length=SEGMENT_CHARS)
        if content is None:
            return f"没有找到cache_id={cache_id}对应的缓存内容，可能已经过期或者传错了ID。"
        return content

    return read_cached_content
