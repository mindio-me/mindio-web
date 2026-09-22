# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""检索工具：行为语义和迁移前的Java实现完全一致（给query，返回工作区里最相关的
片段），只是实现位置换成通过JavaClient调用Java的 /internal/retrieve。新增"上下文
卸载"：结果过长时不直接把全文塞进对话上下文，而是缓存起来，只返回摘要+cache_id，
避免多轮深入研究时上下文失控膨胀（借鉴Deep Agents的文件系统卸载模式）。

citations_sink（可选）：迁移前Java端会把每次检索命中的来源(笔记/收藏)聚合成
citations随"done"事件返回给前端展示，这是现有产品行为，不能在迁移中丢失。这里
用一个调用方传入的可变list承接每次检索到的原始chunk，流式接口（Task 6）处理完
一轮对话后从这个list里读出来构造citations，不需要从工具的文本返回值里反向解析。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.java_client import JavaClient
from app.state import AgentState
from tools.content_cache import ContentCache

TOP_K = 5
OFFLOAD_THRESHOLD_CHARS = 4000
SUMMARY_CHARS = 300


def make_search_workspace_tool(java_client: JavaClient, cache: ContentCache, citations_sink: list | None = None):
    @tool
    async def search_workspace(query: str, state: Annotated[AgentState, InjectedState]) -> str:
        """在用户的笔记和收藏里做语义搜索，返回最相关的片段。当用户的问题可能需要参考他们自己
        写过的笔记或收藏过的内容时调用；如果只是常规聊天或问题已经能从当前对话/当前笔记回答，
        不需要调用。"""
        username = state["username"]
        try:
            # ToolNode默认的handle_tool_errors只兜底LangGraph自己的ToolInvocationError
            # （参数校验失败），普通异常会原样往上抛，直接打断这一轮执行——检索失败
            # （Java 5xx/超时）不应该让整个对话崩掉，这里和写入工具一样自己接住。
            chunks = await java_client.retrieve(username, query, TOP_K)
        except Exception as e:
            return f"搜索失败：{e}"
        if not chunks:
            return "没有搜索到相关内容。"

        if citations_sink is not None:
            citations_sink.extend(chunks)

        text = "\n".join(f"- {c['chunkText']}" for c in chunks)
        if len(text) <= OFFLOAD_THRESHOLD_CHARS:
            return text

        cache_id = cache.put(text)
        summary = text[:SUMMARY_CHARS]
        return (
            f"已找到相关内容，但篇幅较长（{len(text)}字），已缓存（cache_id={cache_id}）。"
            f"摘要：{summary}...如需查看完整内容，调用 read_cached_content(cache_id=\"{cache_id}\")。"
        )

    return search_workspace
