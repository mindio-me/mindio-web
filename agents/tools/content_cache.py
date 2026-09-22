# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""search_workspace的"上下文卸载"用的临时缓存：超长检索结果不直接塞进对话上下文，
先存这里，只把摘要+cache_id返回给模型，需要时再用read_cached_content读完整内容。
纯内存、进程内、按对话生命周期存在——不是数据记录（原始数据永远能从Java的
RetrievalService重新查到），符合spec里"Agent服务不持有任何自己的持久数据"的例外
说明，丢了也无所谓。"""
from __future__ import annotations

import uuid


class ContentCache:
    def __init__(self):
        self._store: dict[str, str] = {}

    def put(self, content: str) -> str:
        cache_id = uuid.uuid4().hex[:12]
        self._store[cache_id] = content
        return cache_id

    def get(self, cache_id: str, offset: int = 0, length: int = 4000) -> str | None:
        content = self._store.get(cache_id)
        if content is None:
            return None
        return content[offset:offset + length]

    def __contains__(self, cache_id: str) -> bool:
        return cache_id in self._store
