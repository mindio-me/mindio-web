"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
笔记分享卡片的 AI 背景图：没给画面描述时先用聊天模型根据笔记写一段，再调图片模型出图。
AI 只出背景，标题由前端叠加，所以提示词严禁出现任何文字。"""
from __future__ import annotations

import base64

from fastapi import APIRouter, HTTPException
from langchain_core.messages import HumanMessage
from pydantic import BaseModel

from app.config import ProviderConfig, use_settings
from app.effective_settings import load_effective_settings
from app.image_gen import ImageGenError, generate_image
from app.java_client import JavaClient
from app.message_content import extract_text_content
from app.models import resolve_chat_model

router = APIRouter()

PROMPT_WRITER = """根据下面这篇笔记的意境，写一段 60-120 字的画面描述，用来生成这篇笔记分享卡片的背景图。
要求：
- 画面抽象或偏意境，不要具象的人物特写；
- 不要任何文字：画面里不得出现任何文字、字母、数字、logo、水印；
- 横幅 1.91:1 构图，下方三分之一留给标题叠加，保持较暗或低对比。
只输出画面描述本身，不要其他说明。

笔记标题：{title}
笔记正文（节选）：
{text}"""

STYLE_SUFFIX = ("No text, no letters, no numbers, no logo, no watermark. "
                "Wide 1.91:1 banner composition, keep the lower third calm and darker for a title overlay.")


class ImageConfig(BaseModel):
    """后端下发的生效生图配置（后端是唯一来源，Key 可能只存在于后端自己的配置里）"""
    provider: str | None = None
    apiKey: str | None = None
    baseUrl: str | None = None
    model: str | None = None


class BackgroundRequest(BaseModel):
    title: str
    text: str
    prompt: str | None = None
    image: ImageConfig | None = None


@router.post("/internal/share-card/background")
async def share_card_background(request: BackgroundRequest) -> dict:
    java_client = JavaClient()
    try:
        cfg = await load_effective_settings(java_client)
    finally:
        await java_client.aclose()
    use_settings(cfg)

    provider = cfg.image_provider
    provider_cfg = cfg.providers.get(provider)
    image_model = cfg.image_model
    img = request.image
    if img and img.provider and img.apiKey:
        provider = img.provider
        base = cfg.providers.get(provider)
        provider_cfg = ProviderConfig(
            api_key=img.apiKey,
            model=base.model if base else "",
            base_url=img.baseUrl or (base.base_url if base else ""),
            chat_path=base.chat_path if base else "/v1/chat/completions",
        )
        image_model = img.model or image_model
    if not provider or provider_cfg is None or not provider_cfg.api_key:
        raise HTTPException(status_code=400, detail="未配置图片生成服务：请先在设置里选择图片生成服务商并填写 Key")

    prompt = (request.prompt or "").strip()
    if not prompt:
        message = PROMPT_WRITER.format(title=request.title, text=request.text[:1500])
        try:
            response = await resolve_chat_model(cfg.default_provider).ainvoke([HumanMessage(content=message)])
        except Exception as e:  # noqa: BLE001
            raise HTTPException(status_code=502, detail=f"生成画面描述失败：{e}") from e
        prompt = extract_text_content(response.content).strip()
        if not prompt:
            raise HTTPException(status_code=502, detail="生成画面描述失败：模型没有返回内容")

    try:
        data, mime = await generate_image(provider, provider_cfg, image_model, f"{prompt}\n{STYLE_SUFFIX}")
    except ImageGenError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
    return {"prompt": prompt, "imageBase64": base64.b64encode(data).decode("ascii"), "mimeType": mime}
