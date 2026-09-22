# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""往当前笔记的时间线块追加一条时间点。写操作是这次会话第一次给 agent 开的口子，
涉及真正修改用户数据，调用前必须经过用户在聊天面板里的人工确认——interrupt() 暂停
执行，恢复时传回的值就是用户的决定（"accept"/"reject"），不是 "accept" 一律当拒绝处理。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from langgraph.types import interrupt

from app.java_client import JavaClient
from app.state import AgentState


def make_add_timeline_item_tool(java_client: JavaClient, block_updates: list | None = None):
    @tool
    async def add_timeline_item(
        date: str, title: str, description: str, state: Annotated[AgentState, InjectedState]
    ) -> str:
        """往用户当前打开的笔记里的时间线块追加一条新的时间点。只有当用户明确希望往笔记里
        添加时间线内容时才调用；这不会修改已有的时间线条目，只会新增一条。调用后会先弹出
        确认卡片让用户接受或拒绝，不会未经确认就写入。"""
        note_id = state.get("current_note_id")
        if not note_id:
            return "当前没有打开的笔记，无法添加时间线条目。请先打开一篇笔记再试。"
        if not date or not title:
            return "日期和标题都不能为空，请补充完整再试。"

        decision = interrupt({
            "blockType": "timeline",
            "noteId": note_id,
            "preview": {"date": date, "title": title, "description": description},
        })
        if decision != "accept":
            return "用户拒绝了这次添加，时间线没有变化。"

        try:
            updated_items = await java_client.append_block_item(
                note_id, "timeline",
                {"date": date, "title": title, "description": description, "link": ""},
            )
        except Exception as e:
            return f"添加失败：{e}"

        if block_updates is not None:
            block_updates.append({"noteId": note_id, "blockType": "timeline", "items": updated_items})

        return f"已添加到时间线：{date} {title}"

    return add_timeline_item
