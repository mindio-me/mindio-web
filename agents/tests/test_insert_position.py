"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from langchain_core.messages import AIMessage

import app.routes.insert_position as route
from app.routes.insert_position import InsertPositionRequest, insert_position

OUTLINE = [
    {"id": "h", "type": "header", "preview": "## 4.3 方案"},
    {"id": "p", "type": "paragraph", "preview": "网关每 5 秒轮询"},
]


class FakeModel:
    def __init__(self, content=None, error=None):
        self.content, self.error = content, error
        self.messages = None

    async def ainvoke(self, messages):
        self.messages = messages
        if self.error:
            raise self.error
        return AIMessage(content=self.content)


def _req(cursor="p"):
    return InsertPositionRequest(outline=OUTLINE, content="新方案：长连接推送", cursorBlockId=cursor)


async def test_parses_block_choice_from_json_in_text(monkeypatch):
    model = FakeModel('好的：{"choice":"block","afterBlockId":"h","reason":"属于 4.3 节"}')
    monkeypatch.setattr(route, "resolve_chat_model", lambda provider: model)

    r = await insert_position(_req())

    assert (r.choice, r.afterBlockId, r.reason) == ("block", "h", "属于 4.3 节")
    assert "新方案：长连接推送" in model.messages[0].content
    assert "[h] header" in model.messages[0].content


async def test_unknown_block_or_garbage_falls_back(monkeypatch):
    monkeypatch.setattr(route, "resolve_chat_model", lambda p: FakeModel('{"choice":"block","afterBlockId":"zz"}'))
    assert (await insert_position(_req())).choice == "cursor"

    monkeypatch.setattr(route, "resolve_chat_model", lambda p: FakeModel("不是 JSON"))
    assert (await insert_position(_req(cursor=None))).choice == "end"


async def test_cursor_choice_without_cursor_becomes_end(monkeypatch):
    monkeypatch.setattr(route, "resolve_chat_model", lambda p: FakeModel('{"choice":"cursor"}'))

    assert (await insert_position(_req(cursor=None))).choice == "end"


async def test_model_error_falls_back(monkeypatch):
    monkeypatch.setattr(route, "resolve_chat_model", lambda p: FakeModel(error=RuntimeError("down")))

    assert (await insert_position(_req())).choice == "cursor"
