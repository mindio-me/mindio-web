"""读取当前项目（项目页打开且选中某个项目时的 current_project_id）关联的笔记，
供模型起草/润色项目简介时参考。只读，不写——和 search_workspace 一样不走
interrupt() 确认卡片。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.java_client import JavaClient, JavaClientError
from app.state import AgentState


def make_get_project_notes_tool(java_client: JavaClient):
    @tool
    async def get_project_notes(state: Annotated[AgentState, InjectedState]) -> str:
        """读取当前项目关联的笔记（标题+正文），用于起草或润色这个项目的简介文案。
        只有用户当前打开"项目"页并选中了某个具体项目时才能调用；如果找不到关联笔记，
        不代表没有可写的材料——可以再用 search_workspace 按项目名/关键词做语义搜索，
        看看有没有虽然没显式关联、但内容相关的笔记。"""
        project_id = state.get("current_project_id")
        if not project_id:
            return "当前没有打开的项目，无法读取关联笔记。请先在项目页选中一个项目再试。"

        try:
            notes = await java_client.get_project_notes(project_id)
        except JavaClientError as e:
            return f"读取项目关联笔记失败：{e}"

        if not notes:
            return "这个项目目前没有显式关联的笔记。可以尝试用 search_workspace 按项目相关关键词搜索，看看有没有虽未关联但内容相关的笔记。"

        parts = [f"【{n['title']}】\n{n['bodyText']}" for n in notes]
        return "\n\n".join(parts)

    return get_project_notes
