"""把模型起草/润色好的项目文案直接写入 description/descriptionZh 字段——brainstorming
阶段用户明确要求跳过确认卡片，理由和 analyze_image 一样：这里是编辑态页面上的
普通文本字段，用户本来就会在原地继续修改，不需要额外一层确认。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.java_client import JavaClient, JavaClientError
from app.state import AgentState

# 只开放这两个字段——后续迭代（自动翻译/正文摘要提炼）要开放更多字段时，
# 在这里扩这个集合即可，不用改调用方/工具签名。
ALLOWED_FIELDS = {"description", "descriptionZh"}


def make_update_project_field_tool(java_client: JavaClient, project_field_updates: list | None = None):
    @tool
    async def update_project_field(
        state: Annotated[AgentState, InjectedState], field: str, value: str
    ) -> str:
        """把起草/润色好的文案写入当前项目的字段。field 只能是 "description"（英文简介）
        或 "descriptionZh"（中文简介）——只写用户当前界面语言对应的那个字段，不要同时写
        两个语言版本。写入是直接生效的，不需要用户二次确认。"""
        project_id = state.get("current_project_id")
        if not project_id:
            return "当前没有打开的项目，无法写入。请先在项目页选中一个项目再试。"

        if field not in ALLOWED_FIELDS:
            return f"暂不支持通过我修改「{field}」这个字段，目前只能写 description/descriptionZh（项目简介）。"

        try:
            await java_client.patch_project_field(project_id, field, value)
        except JavaClientError as e:
            return f"写入失败：{e}"

        if project_field_updates is not None:
            project_field_updates.append({"projectId": project_id, "field": field, "value": value})

        return f"已写入 {field}。"

    return update_project_field
