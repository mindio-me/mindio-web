"""按ID或标题读取用户任意一篇笔记/收藏的全文。之前agent能读到全文的只有"当前笔记"、
"当前笔记关联的素材"、"当前项目的笔记"三种，用户在对话里点名别的笔记时，只能靠
search_workspace拿到零散片段（经常只命中标题单独成的那一块），模型就会回复"只能拿到
标题、读不到正文"。username从InjectedState读，归属校验在Java侧做。长文沿用
search_workspace同款的上下文卸载，阈值放宽一些——用户点名要读的文档，就是要读进来的。"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.java_client import JavaClient
from app.state import AgentState
from tools.content_cache import ContentCache

OFFLOAD_THRESHOLD_CHARS = 12000
SUMMARY_CHARS = 500
TITLE_BRACKETS = "《》<>\"'“”「」【】 "


def _format_document(doc: dict, label: str, cache: ContentCache) -> str:
    header = f"【{doc['title']}】（{label} id={doc['id']}）"
    if doc.get("imageCount"):
        id_arg = "note_id" if label == "笔记" else "clip_id"
        header += (
            f"\n这篇{label}里有{doc['imageCount']}张图片，正文里只保留了图片说明/识别出的文字；"
            f"需要看图时调用 view_image({id_arg}={doc['id']}, index=第几张, question=...)。"
        )
    body = doc.get("bodyText") or ""
    if not body.strip():
        return f"{header}\n（正文为空）"
    if len(body) <= OFFLOAD_THRESHOLD_CHARS:
        return f"{header}\n{body}"

    cache_id = cache.put(body)
    return (
        f"{header}\n正文较长（{len(body)}字），已缓存（cache_id={cache_id}）。开头部分：\n"
        f"{body[:SUMMARY_CHARS]}...\n需要完整内容时调用 read_cached_content(cache_id=\"{cache_id}\")，"
        f"用 offset 分段往后读。"
    )


def make_read_note_tool(java_client: JavaClient, cache: ContentCache):
    @tool
    async def read_note(
        state: Annotated[AgentState, InjectedState],
        note_id: int | None = None,
        title: str | None = None,
    ) -> str:
        """读取用户任意一篇笔记的完整正文（不要求和当前笔记有关联）。用户在对话里点名某篇
        笔记（比如《xxx》）、或者search_workspace搜到的片段不够、需要看整篇时调用。
        知道ID就传note_id（search_workspace结果里的"笔记 id=…"）；只知道标题就传title，
        按标题模糊匹配，匹配到多篇时会列出候选，再用note_id读具体那篇。"""
        username = state["username"]
        try:
            if note_id is None:
                keyword = (title or "").strip(TITLE_BRACKETS)
                if not keyword:
                    return "需要提供 note_id 或 title 其中之一。"
                matches = await java_client.find_notes_by_title(username, keyword)
                if not matches:
                    return f"没有找到标题包含「{keyword}」的笔记。可以换个关键词，或用 search_workspace 按内容搜索。"
                exact = [m for m in matches if m["title"].strip() == keyword]
                if len(exact) == 1:
                    note_id = exact[0]["id"]
                elif len(matches) == 1:
                    note_id = matches[0]["id"]
                else:
                    listing = "\n".join(f"- {m['title']}（笔记 id={m['id']}）" for m in matches)
                    return f"标题包含「{keyword}」的笔记有多篇，请用 note_id 读取需要的那篇：\n{listing}"

            doc = await java_client.read_note(username, note_id)
        except Exception as e:
            return f"读取笔记失败：{e}"
        if doc is None:
            return f"找不到 id={note_id} 的笔记（不存在或不属于当前用户）。"
        return _format_document(doc, "笔记", cache)

    return read_note


def make_read_clip_tool(java_client: JavaClient, cache: ContentCache):
    @tool
    async def read_clip(clip_id: int, state: Annotated[AgentState, InjectedState]) -> str:
        """读取用户某条收藏（网页剪藏等素材）的完整正文。search_workspace结果里出现"收藏 id=…"、
        需要看整篇原文时调用。"""
        try:
            doc = await java_client.read_clip(state["username"], clip_id)
        except Exception as e:
            return f"读取收藏失败：{e}"
        if doc is None:
            return f"找不到 id={clip_id} 的收藏（不存在或不属于当前用户）。"
        return _format_document(doc, "收藏", cache)

    return read_clip
