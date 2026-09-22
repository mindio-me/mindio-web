# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""让agent描述用户当前笔记里某张图片的内容（视觉理解/image captioning），写回图片
block的caption字段。和add_gallery_item等"写入"工具不同，这里不走interrupt()确认
卡片——分析结果直接写入（brainstorming阶段用户明确要求跳过确认卡片，理由是分析结果
风险远低于新增笔记内容）。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.messages import HumanMessage
from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.config import settings
from app.java_client import JavaClient, JavaClientError
from app.message_content import extract_text_content
from app.models import resolve_chat_model
from app.state import AgentState

CAPTION_PROMPT = (
    "请用一到两句话描述这张图片的内容、场景或要点，输出纯文字描述，不要加任何前缀、"
    "引号或解释性文字。"
)


def make_analyze_image_tool(java_client: JavaClient, media_block_updates: list | None = None):
    @tool
    async def analyze_image(
        state: Annotated[AgentState, InjectedState], index: int | None = None
    ) -> str:
        """描述用户当前笔记里一张图片的视觉内容，并把结果写入这张图片下方的说明文字
        （caption）。当笔记里只有一张图片时不需要传index；有多张时，index是这张图片
        在笔记里从上到下的顺序号（从1开始）。不确定具体是第几张时不要瞎猜，留空index，
        工具会返回图片数量，你据此向用户确认。"""
        note_id = state.get("current_note_id")
        if not note_id:
            return "当前没有打开的笔记，无法分析图片。请先打开一篇笔记再试。"

        try:
            blocks = await java_client.list_media_blocks(note_id)
        except JavaClientError as e:
            return f"读取笔记里的图片列表失败：{e}"
        images = [b for b in blocks if b.get("blockType") == "image"]

        if not images:
            return "这篇笔记里没有图片。"
        if len(images) == 1:
            target = images[0]
        elif index is not None and 1 <= index <= len(images):
            target = images[index - 1]
        else:
            return f"这篇笔记里有{len(images)}张图片，请告诉我具体是第几张（从上到下数，从1开始）。"

        try:
            media = await java_client.get_block_media(note_id, target["blockId"])
        except JavaClientError as e:
            return f"读取图片文件失败：{e}"

        data_uri = f"data:{media['mimeType']};base64,{media['base64Data']}"
        model = resolve_chat_model(settings.default_provider)
        message = HumanMessage(content=[
            {"type": "text", "text": CAPTION_PROMPT},
            {"type": "image_url", "image_url": {"url": data_uri}},
        ])
        try:
            response = await model.ainvoke([message])
        except Exception as e:
            return f"图片分析失败：{e}（可能是当前默认模型不支持图片理解，需要换成支持视觉的 provider）"
        caption = extract_text_content(response.content).strip()
        if not caption:
            return "没能生成图片描述，换张图片试试？"

        try:
            updated_data = await java_client.patch_media_block(note_id, target["blockId"], {"caption": caption})
        except JavaClientError as e:
            return f"图片描述已生成，但写回笔记失败：{e}（描述：{caption}）"

        if media_block_updates is not None:
            media_block_updates.append({
                "noteId": note_id, "blockId": target["blockId"],
                "blockType": "image", "data": updated_data,
            })

        return f"已生成图片描述并写入笔记：{caption}"

    return analyze_image
