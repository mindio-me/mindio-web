# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""往当前笔记的任务清单块追加一条待办。同 add_reference_item，写入前必须经过人工确认。"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from langgraph.types import interrupt

from app.java_client import JavaClient
from app.state import AgentState


def make_add_checklist_item_tool(java_client: JavaClient, block_updates: list | None = None):
    @tool
    async def add_checklist_item(
        text: str, checked: bool, state: Annotated[AgentState, InjectedState]
    ) -> str:
        """往用户当前打开的笔记里的任务清单块追加一条待办事项。只有当用户明确希望往笔记里
        添加任务/待办时才调用；这不会修改已有的条目，只会新增一条。调用后会先弹出确认卡片
        让用户接受或拒绝，不会未经确认就写入。"""
        note_id = state.get("current_note_id")
        if not note_id:
            return "当前没有打开的笔记，无法添加任务。请先打开一篇笔记再试。"
        if not text:
            return "任务内容不能为空，请补充完整再试。"

        decision = interrupt({
            "blockType": "checklist",
            "noteId": note_id,
            "preview": {"text": text, "checked": checked},
        })
        if decision != "accept":
            return "用户拒绝了这次添加，任务清单没有变化。"

        try:
            updated_items = await java_client.append_block_item(
                note_id, "checklist",
                {"text": text, "checked": checked},
            )
        except Exception as e:
            return f"添加失败：{e}"

        if block_updates is not None:
            block_updates.append({"noteId": note_id, "blockType": "checklist", "items": updated_items})

        return f"已添加任务：{text}"

    return add_checklist_item
