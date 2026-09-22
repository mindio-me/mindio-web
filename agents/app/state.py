# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""Agent的对话状态。todos字段借鉴Deep Agents的"任务规划器"模式（不依赖deepagents包，
只是同样的思路）：让模型在处理复杂请求时先拆解步骤、逐步勾选完成，而不是漫无目的地
一次性乱跑。"""
from __future__ import annotations

from typing import Annotated, Literal

from langgraph.graph.message import add_messages
from typing_extensions import TypedDict


class TodoItem(TypedDict):
    id: str
    task: str
    status: Literal["pending", "in_progress", "completed"]


class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    todos: list[TodoItem]
    conversation_id: str
    username: str
    current_note_context: str | None
    current_note_id: int | None
