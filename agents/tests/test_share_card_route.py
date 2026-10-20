"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
import base64

import pytest
from fastapi import HTTPException
from langchain_core.messages import AIMessage

import app.routes.share_card as route
from app.config import use_settings
from app.image_gen import ImageGenError
from app.routes.share_card import BackgroundRequest, share_card_background


class FakeModel:
    def __init__(self, content="  一片薄雾中的山谷  "):
        self.content = content
        self.messages = None
        self.calls = 0

    async def ainvoke(self, messages):
        self.calls += 1
        self.messages = messages
        return AIMessage(content=self.content)


@pytest.fixture(autouse=True)
def _image_configured(monkeypatch, test_settings):
    import dataclasses
    cfg = dataclasses.replace(test_settings, image_provider="openai", image_model="")

    async def fake_load(java_client, base=None):
        return cfg

    monkeypatch.setattr(route, "load_effective_settings", fake_load)
    return cfg


def _fake_gen(captured, error=None):
    async def gen(provider, cfg, model, prompt, **kw):
        captured.update(provider=provider, model=model, prompt=prompt, key=cfg.api_key)
        if error:
            raise error
        return b"IMG", "image/png"
    return gen


async def test_writes_prompt_with_chat_model_when_missing(monkeypatch):
    model, captured = FakeModel(), {}
    monkeypatch.setattr(route, "resolve_chat_model", lambda provider: model)
    monkeypatch.setattr(route, "generate_image", _fake_gen(captured))

    r = await share_card_background(BackgroundRequest(title="夜读笔记", text="深夜的图书馆很安静", prompt=None))

    sent = model.messages[0].content
    assert "夜读笔记" in sent and "深夜的图书馆很安静" in sent
    assert "不要任何文字" in sent
    assert r["prompt"] == "一片薄雾中的山谷"
    assert captured["prompt"].startswith("一片薄雾中的山谷")
    assert captured["provider"] == "openai"
    assert r["mimeType"] == "image/png"
    assert base64.b64decode(r["imageBase64"]) == b"IMG"


async def test_uses_given_prompt_without_chat_model(monkeypatch):
    model, captured = FakeModel(), {}
    monkeypatch.setattr(route, "resolve_chat_model", lambda provider: model)
    monkeypatch.setattr(route, "generate_image", _fake_gen(captured))

    r = await share_card_background(BackgroundRequest(title="t", text="x", prompt="山谷"))

    assert model.calls == 0
    assert r["prompt"] == "山谷"
    assert captured["prompt"].startswith("山谷")
    assert "No text, no letters" in captured["prompt"]
    assert "1.91:1" in captured["prompt"]


async def test_rejects_when_image_provider_not_configured(monkeypatch, test_settings):
    async def fake_load(java_client, base=None):
        return test_settings  # image_provider == ""

    monkeypatch.setattr(route, "load_effective_settings", fake_load)
    with pytest.raises(HTTPException) as e:
        await share_card_background(BackgroundRequest(title="t", text="x", prompt="p"))
    assert e.value.status_code == 400
    assert "图片生成" in e.value.detail


async def test_maps_provider_error_to_502_with_message(monkeypatch):
    monkeypatch.setattr(route, "generate_image", _fake_gen({}, ImageGenError("401 invalid key")))
    with pytest.raises(HTTPException) as e:
        await share_card_background(BackgroundRequest(title="t", text="x", prompt="p"))
    assert e.value.status_code == 502
    assert "401" in e.value.detail
