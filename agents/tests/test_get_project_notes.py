"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from tools.get_project_notes import make_get_project_notes_tool


class FakeJavaClient:
    def __init__(self, notes=None):
        self._notes = notes if notes is not None else []
        self.calls: list[int] = []

    async def get_project_notes(self, project_id: int) -> list[dict]:
        self.calls.append(project_id)
        return self._notes


def _agent_state(current_project_id: int | None = None) -> dict:
    return {
        "messages": [], "todos": [], "conversation_id": "alice", "username": "alice",
        "current_note_context": None, "current_note_id": None,
        "current_project_context": None, "current_project_id": current_project_id,
    }


async def test_returns_formatted_notes_when_project_has_linked_notes():
    java = FakeJavaClient([
        {"id": 1, "title": "开发日志", "bodyText": "第一段正文"},
        {"id": 2, "title": "复盘笔记", "bodyText": "复盘内容"},
    ])
    tool_ = make_get_project_notes_tool(java)

    result = await tool_.ainvoke({"state": _agent_state(current_project_id=9)})

    assert java.calls == [9]
    assert "开发日志" in result
    assert "第一段正文" in result
    assert "复盘笔记" in result


async def test_returns_guidance_text_when_no_linked_notes():
    java = FakeJavaClient([])
    tool_ = make_get_project_notes_tool(java)

    result = await tool_.ainvoke({"state": _agent_state(current_project_id=9)})

    assert java.calls == [9]
    assert "没有" in result


async def test_returns_guidance_when_no_current_project():
    java = FakeJavaClient([{"id": 1, "title": "x", "bodyText": "y"}])
    tool_ = make_get_project_notes_tool(java)

    result = await tool_.ainvoke({"state": _agent_state(current_project_id=None)})

    assert java.calls == []
    assert "项目" in result
