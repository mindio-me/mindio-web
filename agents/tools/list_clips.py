"""按域名/标签/关键词精确列出用户的收藏。search_workspace是语义检索，只回topK条片段，
而且收藏的URL不在索引文本里——"列出所有github上的收藏"这类请求它天然答不全，模型就会
回复"你好像没有相关收藏"。这里走Java的结构化筛选（SQL），按收藏时间倒序，总数和
实际返回条数一起告诉模型，避免把"只显示了前N条"当成"一共就N条"。"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.java_client import JavaClient
from app.state import AgentState

DEFAULT_LIMIT = 50


def _format_item(item: dict) -> str:
    line = f"- {item['title']}"
    if item.get("url"):
        line += f" — {item['url']}"
    meta = [f"收藏 id={item['id']}"]
    if item.get("tags"):
        meta.append("标签：" + "、".join(item["tags"]))
    if item.get("savedAt"):
        meta.append("收藏于 " + item["savedAt"][:10])
    return f"{line}（{'，'.join(meta)}）"


def make_list_clips_tool(java_client: JavaClient):
    @tool
    async def list_clips(
        state: Annotated[AgentState, InjectedState],
        domain: str | None = None,
        tag: str | None = None,
        keyword: str | None = None,
        limit: int = DEFAULT_LIMIT,
    ) -> str:
        """按条件精确列出用户的收藏（含从浏览器导入的书签），按收藏时间从新到旧排序。
        用户要"列出/找出所有某网站的收藏"、"某个标签下的收藏"、"标题或链接里带某词的收藏"时调用，
        不要用 search_workspace 代替（语义搜索只返回少量片段，列不全）。
        domain：网站域名，如 "github.com"，会同时匹配子域（gist.github.com）；
        tag：标签名，精确匹配；keyword：在标题、摘要、链接里模糊匹配；
        三个条件可任意组合，都不传就是列出全部收藏。limit 最多 200。
        需要看某条收藏的正文时，再用 read_clip(clip_id=...)。"""
        try:
            result = await java_client.list_clips(state["username"], domain=domain, tag=tag, keyword=keyword, limit=limit)
        except Exception as e:
            return f"列出收藏失败：{e}"

        items = result.get("items") or []
        total = result.get("total", len(items))
        if not items:
            return "没有符合条件的收藏。"

        header = f"共 {total} 条符合条件的收藏"
        if total > len(items):
            header += f"，以下是最新的 {len(items)} 条（需要更多可以调大 limit，最多 200；再多就加筛选条件缩小范围）"
        return header + "：\n" + "\n".join(_format_item(i) for i in items)

    return list_clips
