# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""往当前笔记的媒体画廊块追加一条素材。同 add_timeline_item，写入前必须经过人工确认。
只处理"添加一个网络视频链接"这个场景（跟 nuxt-frontend 那边一致：画廊块的视频类型只支持
网络链接，本地视频上传不在这次范围内，图片/音频上传同样是前端 UI 交互，agent 不处理）。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from langgraph.types import interrupt

from app.java_client import JavaClient
from app.state import AgentState


def make_add_gallery_item_tool(java_client: JavaClient, block_updates: list | None = None):
    @tool
    async def add_gallery_item(
        embedUrl: str, caption: str, state: Annotated[AgentState, InjectedState]
    ) -> str:
        """往用户当前打开的笔记里的媒体画廊块追加一个网络视频链接（YouTube/B站/Vimeo/抖音）。
        只有当用户明确希望往笔记里添加视频素材时才调用；这不会修改已有的素材，只会新增一条。
        调用后会先弹出确认卡片让用户接受或拒绝，不会未经确认就写入。"""
        note_id = state.get("current_note_id")
        if not note_id:
            return "当前没有打开的笔记，无法添加素材。请先打开一篇笔记再试。"
        if not embedUrl:
            return "视频链接不能为空，请补充完整再试。"

        decision = interrupt({
            "blockType": "mediaGallery",
            "noteId": note_id,
            "preview": {"type": "video", "embedUrl": embedUrl, "caption": caption},
        })
        if decision != "accept":
            return "用户拒绝了这次添加，画廊没有变化。"

        try:
            updated_items = await java_client.append_block_item(
                note_id, "mediaGallery",
                {"type": "video", "url": "", "embedUrl": embedUrl, "caption": caption},
            )
        except Exception as e:
            return f"添加失败：{e}"

        if block_updates is not None:
            block_updates.append({"noteId": note_id, "blockType": "mediaGallery", "items": updated_items})

        return f"已添加到画廊：{embedUrl}"

    return add_gallery_item
