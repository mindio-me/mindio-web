# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""任务规划工具：借鉴Deep Agents的"动态任务规划器"模式（不依赖deepagents包，只是
同样的思路）——让模型在处理复杂研究请求时先拆解步骤、逐步勾选完成，而不是漫无目的
地一次性乱跑。真正的状态更新通过LangGraph的Command机制写回AgentState.todos，
不是靠返回值本身。"""
from __future__ import annotations

from typing import Annotated

from langchain_core.messages import ToolMessage
from langchain_core.tools import InjectedToolCallId, tool
from langgraph.types import Command

from app.state import TodoItem


@tool
def update_todo_list(todos: list[TodoItem], tool_call_id: Annotated[str, InjectedToolCallId]) -> Command:
    """更新当前的任务清单与执行进度。处理复杂/多步骤的研究请求时，先调用一次拆出步骤，
    每完成一个阶段性目标或发现新步骤时再次调用更新状态。todos是完整的任务清单
    （不是增量），每项包含id/task/status（pending|in_progress|completed）。"""
    return Command(update={
        "todos": todos,
        "messages": [ToolMessage("任务清单已更新", tool_call_id=tool_call_id)],
    })
