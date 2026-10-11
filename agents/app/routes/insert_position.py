"""回复上「插入到笔记」按钮的位置判断：给模型看笔记大纲、光标位置和要插入的内容，返回
"光标处 / 文末 / 某块之后"。一次普通模型调用，不进 agent 图。模型失败或输出不合法时退回
光标处（没有光标则文末），不让这一步卡住插入。
详见 docs/superpowers/specs/2026-10-11-agent-note-editing-design.md。
"""
from __future__ import annotations

import json
import re

from fastapi import APIRouter
from langchain_core.messages import HumanMessage
from pydantic import BaseModel

from app.config import use_settings
from app.effective_settings import load_effective_settings
from app.java_client import JavaClient
from app.message_content import extract_text_content
from app.models import resolve_chat_model

router = APIRouter()

PROMPT = """你要决定把一段内容插入到用户笔记的什么位置。

笔记大纲（每行：[块id] 类型 开头文字）：
{outline}

用户光标所在块：{cursor}

要插入的内容：
{content}

只输出一个 JSON 对象，不要其他文字：
{{"choice": "cursor" | "end" | "block", "afterBlockId": "<choice 为 block 时填块 id>", "reason": "<一句话理由>"}}
规则：内容和光标所在位置的上下文相关，或者看不出更合适的位置时选 cursor；内容是总结、补充、附录性质时选 end；
只有当某一节明显更合适（主题明确对应某个标题下的内容）时才选 block，afterBlockId 填应当插在其后的那一块。"""


class OutlineBlock(BaseModel):
    id: str
    type: str
    preview: str


class InsertPositionRequest(BaseModel):
    outline: list[OutlineBlock]
    content: str
    cursorBlockId: str | None = None


class InsertPositionResponse(BaseModel):
    choice: str
    afterBlockId: str | None = None
    reason: str = ""


@router.post("/internal/insert-position")
async def insert_position(request: InsertPositionRequest) -> InsertPositionResponse:
    fallback = InsertPositionResponse(choice="cursor" if request.cursorBlockId else "end")
    java_client = JavaClient()
    try:
        cfg = await load_effective_settings(java_client)
    finally:
        await java_client.aclose()
    use_settings(cfg)

    outline = "\n".join(f"[{b.id}] {b.type} {b.preview}" for b in request.outline)
    prompt = PROMPT.format(outline=outline or "（空笔记）", cursor=request.cursorBlockId or "（无）",
                           content=request.content[:2000])
    try:
        response = await resolve_chat_model(cfg.default_provider).ainvoke([HumanMessage(content=prompt)])
    except Exception:
        return fallback
    return _parse(extract_text_content(response.content), request) or fallback


def _parse(text: str, request: InsertPositionRequest) -> InsertPositionResponse | None:
    match = re.search(r"\{.*\}", text, re.S)
    if not match:
        return None
    try:
        data = json.loads(match.group(0))
    except ValueError:
        return None
    choice = data.get("choice")
    reason = str(data.get("reason") or "")
    if choice == "end":
        return InsertPositionResponse(choice="end", reason=reason)
    if choice == "cursor":
        return InsertPositionResponse(choice="cursor" if request.cursorBlockId else "end", reason=reason)
    if choice == "block" and data.get("afterBlockId") in {b.id for b in request.outline}:
        return InsertPositionResponse(choice="block", afterBlockId=data["afterBlockId"], reason=reason)
    return None
