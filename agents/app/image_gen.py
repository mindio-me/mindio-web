"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
分享卡片背景图生成：调用 OpenAI / 豆包(火山方舟 Seedream) 的图片生成接口，返回图片字节和 mime。
两家都用各自的 Key 和 base_url（复用对话模型的 ProviderConfig），只是模型名和路径不同。"""
from __future__ import annotations

import base64

import httpx

from app.config import ProviderConfig

IMAGE_TIMEOUT_SECONDS = 180.0

# provider -> (路径, 默认模型, 请求体里除 model/prompt 外的固定参数)
_PROVIDERS = {
    # gpt-image-1 始终返回 b64_json，不接受 response_format 参数
    "openai": ("/v1/images/generations", "gpt-image-1", {"size": "1536x1024", "n": 1}),
    "doubao": ("/api/v3/images/generations", "doubao-seedream-4-0-250828",
               {"size": "1600x840", "response_format": "b64_json", "watermark": False}),
}


class ImageGenError(Exception):
    """图片生成失败：网络错误、非2xx响应或响应里没有图片。消息会直接展示给用户。"""


def _sniff_mime(data: bytes) -> str:
    if data.startswith(b"\xff\xd8"):
        return "image/jpeg"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp"
    return "image/png"


async def generate_image(provider: str, cfg: ProviderConfig, model: str, prompt: str,
                         client: httpx.AsyncClient | None = None) -> tuple[bytes, str]:
    if provider not in _PROVIDERS:
        raise ImageGenError(f"不支持的图片生成服务：{provider}")
    path, default_model, extra = _PROVIDERS[provider]
    url = cfg.base_url.rstrip("/") + path
    body = {"model": model or default_model, "prompt": prompt, **extra}
    own = client is None
    http = client or httpx.AsyncClient(timeout=IMAGE_TIMEOUT_SECONDS)
    try:
        try:
            resp = await http.post(url, json=body, headers={"Authorization": f"Bearer {cfg.api_key}"})
        except httpx.HTTPError as e:
            raise ImageGenError(f"图片生成请求失败：{e}") from e
    finally:
        if own:
            await http.aclose()
    if resp.status_code >= 300:
        raise ImageGenError(f"图片生成失败 {resp.status_code}：{resp.text[:300]}")
    try:
        data = base64.b64decode(resp.json()["data"][0]["b64_json"])
    except (ValueError, KeyError, IndexError, TypeError) as e:
        raise ImageGenError("图片生成失败：响应里没有图片数据") from e
    return data, _sniff_mime(data)
