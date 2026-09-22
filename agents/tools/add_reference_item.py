# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""往当前笔记的参考资料块追加一条资料（链接/文档/笔记）。同 add_timeline_item，写入前
必须经过人工确认。这个工具只处理"添加一条外部链接"这个最简单的场景——从收藏/笔记搜索
选择、上传文档，都是前端 UI 交互（见 nuxt-frontend/utils/editorjsReferencesTool.js），
agent 只走最基础的手动链接添加路径。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from langgraph.types import interrupt

from app.java_client import JavaClient
from app.state import AgentState


def make_add_reference_item_tool(java_client: JavaClient, block_updates: list | None = None):
    @tool
    async def add_reference_item(
        title: str, url: str, note: str, state: Annotated[AgentState, InjectedState]
    ) -> str:
        """往用户当前打开的笔记里的参考资料块追加一条链接类型的资料。只有当用户明确希望
        往笔记里添加参考资料/引用链接时才调用；这不会修改已有的条目，只会新增一条。调用后
        会先弹出确认卡片让用户接受或拒绝，不会未经确认就写入。"""
        note_id = state.get("current_note_id")
        if not note_id:
            return "当前没有打开的笔记，无法添加参考资料。请先打开一篇笔记再试。"
        if not title or not url:
            return "标题和链接都不能为空，请补充完整再试。"

        decision = interrupt({
            "blockType": "references",
            "noteId": note_id,
            "preview": {"title": title, "url": url, "note": note},
        })
        if decision != "accept":
            return "用户拒绝了这次添加，参考资料没有变化。"

        try:
            updated_items = await java_client.append_block_item(
                note_id, "references",
                {"kind": "link", "title": title, "url": url, "note": note},
            )
        except Exception as e:
            return f"添加失败：{e}"

        if block_updates is not None:
            block_updates.append({"noteId": note_id, "blockType": "references", "items": updated_items})

        return f"已添加参考资料：{title}"

    return add_reference_item
