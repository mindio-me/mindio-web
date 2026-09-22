# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""
自定义LangGraph checkpointer：复用官方 InMemorySaver 的全部读写逻辑（版本管理、
pending writes、parent chain 这些细节很复杂，直接继承比重新实现更可靠），只在
处理一轮对话前后，把内存态整体序列化成一个不透明的字符串blob，通过 JavaClient
读/写到Java那边。Java是唯一的持久化来源——这个checkpointer实例本身只是"跑这一轮
agent时的临时内存表示"，不跨进程持久化任何东西，符合spec里"Agent服务无状态、
Java是单一数据源"的原则。

用法（在Task 5/6构建/调用agent图的地方）：每次处理一轮对话前先 `await hydrate()`，
处理完（不管成功还是失败）都要 `await flush()`，否则这轮产生的状态会丢失。
"""
from __future__ import annotations

import base64
import pickle
from collections import defaultdict

from langgraph.checkpoint.memory import InMemorySaver

from app.java_client import JavaClient


class JavaBackedCheckpointer(InMemorySaver):
    def __init__(self, java_client: JavaClient, conversation_id: str):
        super().__init__()
        self._java_client = java_client
        self._conversation_id = conversation_id

    async def hydrate(self) -> None:
        """处理一轮对话前调用：把Java里存的状态（如果有）加载进内存。"""
        blob = await self._java_client.get_state(self._conversation_id)
        if not blob:
            return
        data = pickle.loads(base64.b64decode(blob))
        for thread_id, ns_map in data["storage"].items():
            self.storage[thread_id] = defaultdict(dict, ns_map)
        self.writes = defaultdict(dict, data["writes"])
        self.blobs = dict(data["blobs"])

    async def flush(self) -> None:
        """处理完一轮对话后调用（无论成功或失败都应该调，避免这轮的状态丢失）：
        把内存里的最新状态整体存回Java。"""
        data = {
            "storage": {tid: dict(ns_map) for tid, ns_map in self.storage.items()},
            "writes": dict(self.writes),
            "blobs": dict(self.blobs),
        }
        blob = base64.b64encode(pickle.dumps(data)).decode("ascii")
        await self._java_client.put_state(self._conversation_id, blob)
