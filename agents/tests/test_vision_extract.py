# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

from langchain_core.messages import AIMessage

import app.routes.vision as vision_route
from app.routes.vision import VisionExtractRequest, vision_extract


class FakeModel:
    def __init__(self, content):
        self._content = content

    async def ainvoke(self, messages):
        return AIMessage(content=self._content)


async def test_vision_extract_returns_stripped_text(monkeypatch):
    monkeypatch.setattr(vision_route, "resolve_chat_model", lambda provider: FakeModel("  识别到的文字  "))

    response = await vision_extract(VisionExtractRequest(imageDataUri="data:image/png;base64,abc"))

    assert response.text == "识别到的文字"


async def test_vision_extract_handles_list_shaped_content_blocks(monkeypatch):
    monkeypatch.setattr(
        vision_route, "resolve_chat_model",
        lambda provider: FakeModel([{"type": "text", "text": "识别到的文字"}]),
    )

    response = await vision_extract(VisionExtractRequest(imageDataUri="data:image/png;base64,abc"))

    assert response.text == "识别到的文字"


async def test_vision_extract_sends_image_block_and_default_prompt(monkeypatch):
    captured = {}

    class CapturingModel:
        async def ainvoke(self, messages):
            captured["messages"] = messages
            return AIMessage(content="文字")

    monkeypatch.setattr(vision_route, "resolve_chat_model", lambda provider: CapturingModel())

    await vision_extract(VisionExtractRequest(imageDataUri="data:image/png;base64,abc"))

    blocks = captured["messages"][0].content
    assert blocks[0] == {"type": "text", "text": vision_route.DEFAULT_PROMPT}
    assert blocks[1] == {"type": "image_url", "image_url": {"url": "data:image/png;base64,abc"}}


async def test_vision_extract_uses_custom_prompt_when_provided(monkeypatch):
    captured = {}

    class CapturingModel:
        async def ainvoke(self, messages):
            captured["messages"] = messages
            return AIMessage(content="文字")

    monkeypatch.setattr(vision_route, "resolve_chat_model", lambda provider: CapturingModel())

    await vision_extract(VisionExtractRequest(imageDataUri="data:image/png;base64,abc", prompt="只识别数字"))

    blocks = captured["messages"][0].content
    assert blocks[0]["text"] == "只识别数字"
