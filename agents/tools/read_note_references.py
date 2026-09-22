# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""读取当前笔记已经关联(NoteClipRef)的素材全文，供agent在用户要求"总结一下我关联的
这些资料"时使用——现有的current_note_context只带笔记自己的正文，不带关联进来的
素材，这个工具补上这一块。note_id不作为模型可见参数，而是从InjectedState里的
current_note_id读（和search_workspace从state读username是同一个套路），模型不需要
也不应该自己编一个笔记ID。沿用search_workspace同款的上下文卸载：内容多的话先返回
摘要+cache_id，需要时再调read_cached_content读全文。"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.java_client import JavaClient
from app.state import AgentState
from tools.content_cache import ContentCache

OFFLOAD_THRESHOLD_CHARS = 4000
SUMMARY_CHARS = 300


def make_read_note_references_tool(java_client: JavaClient, cache: ContentCache):
    @tool
    async def read_note_references(state: Annotated[AgentState, InjectedState]) -> str:
        """读取当前这篇笔记已经关联的所有素材（笔记里"关联"进来的收藏）的完整内容。当用户
        要求总结、整理或分析"这篇笔记关联的资料"时调用。"""
        note_id = state.get("current_note_id")
        if note_id is None:
            return "当前不在编辑笔记，没有可关联的素材。"

        try:
            items = await java_client.get_note_references(note_id)
        except Exception as e:
            return f"读取关联素材失败：{e}"
        if not items:
            return "这篇笔记还没有关联任何素材。"

        text = "\n\n".join(f"【{i['title']}】\n{i['content']}" for i in items)
        if len(text) <= OFFLOAD_THRESHOLD_CHARS:
            return text

        cache_id = cache.put(text)
        summary = text[:SUMMARY_CHARS]
        return (
            f"已找到{len(items)}条关联素材，但篇幅较长（{len(text)}字），已缓存"
            f"（cache_id={cache_id}）。摘要：{summary}...如需查看完整内容，调用 "
            f"read_cached_content(cache_id=\"{cache_id}\")。"
        )

    return read_note_references
