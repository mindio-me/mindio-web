"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
import base64
import json

import httpx
import pytest

from app.config import ProviderConfig
from app.image_gen import ImageGenError, generate_image

PNG = b"\x89PNG\r\n fake"
B64 = base64.b64encode(PNG).decode()


def _client(handler):
    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


async def test_openai_request_shape():
    captured = {}

    def handler(request):
        captured["url"] = str(request.url)
        captured["auth"] = request.headers["authorization"]
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"data": [{"b64_json": B64}]})

    cfg = ProviderConfig(api_key="sk-o", model="chat", base_url="https://api.openai.com/")
    data, mime = await generate_image("openai", cfg, "", "a valley", client=_client(handler))

    assert captured["url"] == "https://api.openai.com/v1/images/generations"
    assert captured["auth"] == "Bearer sk-o"
    assert captured["body"]["model"] == "gpt-image-1"
    assert captured["body"]["size"] == "1536x1024"
    assert captured["body"]["prompt"] == "a valley"
    assert (data, mime) == (PNG, "image/png")


async def test_doubao_request_shape():
    captured = {}

    def handler(request):
        captured["url"] = str(request.url)
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"data": [{"b64_json": B64}]})

    cfg = ProviderConfig(api_key="k", model="chat", base_url="https://ark.cn-beijing.volces.com")
    await generate_image("doubao", cfg, "", "p", client=_client(handler))

    assert captured["url"] == "https://ark.cn-beijing.volces.com/api/v3/images/generations"
    body = captured["body"]
    assert body["watermark"] is False
    assert body["model"] == "doubao-seedream-4-0-250828"
    assert body["size"] == "1600x840"
    assert body["response_format"] == "b64_json"


async def test_explicit_model_wins():
    captured = {}

    def handler(request):
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"data": [{"b64_json": B64}]})

    cfg = ProviderConfig(api_key="k", model="chat", base_url="https://api.openai.com")
    await generate_image("openai", cfg, "my-model", "p", client=_client(handler))
    assert captured["body"]["model"] == "my-model"


async def test_raises_on_http_error():
    def handler(request):
        return httpx.Response(401, json={"error": {"message": "bad key"}})

    cfg = ProviderConfig(api_key="k", model="chat", base_url="https://api.openai.com")
    with pytest.raises(ImageGenError) as e:
        await generate_image("openai", cfg, "", "p", client=_client(handler))
    assert "401" in str(e.value)
