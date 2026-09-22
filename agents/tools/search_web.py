# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""联网搜索工具：包装Java的 /internal/search-web 接口（内部转发到现有的
WebSearchProviderResolver/Tavily/RSS）。只读，模型自主决定何时调用（和
search_workspace一样，不需要人工确认）。命中的结果标成WEB类型的citation交给
前端渲染"保存到笔记"按钮——最终建SourceClip走的是Java既有的抓取全文流程，这里
只需要标题/URL/摘要，不做上下文卸载（搜索结果本身就是摘要量级）。"""
from __future__ import annotations

from langchain_core.tools import tool

from app.java_client import JavaClient

TOP_K = 5


def make_search_web_tool(java_client: JavaClient, citations_sink: list | None = None):
    @tool
    async def search_web(query: str) -> str:
        """联网搜索，查找mindio工作区之外的公开资料（新闻、文章、参考页面）。当用户的问题
        需要最新信息，或者工作区里明显没有相关资料时调用。"""
        try:
            results = await java_client.search_web(query, TOP_K)
        except Exception as e:
            return f"联网搜索失败：{e}"
        if not results:
            return "没有搜索到相关网页。"

        if citations_sink is not None:
            for r in results:
                citations_sink.append({
                    "sourceType": "WEB",
                    "sourceUrl": r["url"],
                    "title": r["title"],
                })

        return "\n".join(f"- {r['title']}：{r['excerpt']}（{r['url']}）" for r in results)

    return search_web
