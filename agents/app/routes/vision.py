# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""一次性"图片→文字"接口，供Java的ImageOcrJob调用——和agent对话完全无关，不进
LangGraph图、不用checkpointer，一次HTTP请求对应一次模型调用。详见
docs/superpowers/specs/2026-09-05-image-ocr-pipeline-design.md。
"""
from __future__ import annotations

from fastapi import APIRouter
from langchain_core.messages import HumanMessage
from pydantic import BaseModel

from app.config import settings
from app.message_content import extract_text_content
from app.models import resolve_chat_model

router = APIRouter()

DEFAULT_PROMPT = (
    "识别并转写这张图片里的所有文字，只输出文字内容本身，不要添加任何解释、"
    "标点修饰或前后缀。如果图片中没有文字，输出空字符串。"
)


class VisionExtractRequest(BaseModel):
    imageDataUri: str
    prompt: str | None = None


class VisionExtractResponse(BaseModel):
    text: str


@router.post("/internal/vision-extract")
async def vision_extract(request: VisionExtractRequest) -> VisionExtractResponse:
    model = resolve_chat_model(settings.default_provider)
    message = HumanMessage(content=[
        {"type": "text", "text": request.prompt or DEFAULT_PROMPT},
        {"type": "image_url", "image_url": {"url": request.imageDataUri}},
    ])
    response = await model.ainvoke([message])
    text = extract_text_content(response.content)
    return VisionExtractResponse(text=text.strip())
