# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""让agent把用户当前笔记里某段音频/录音转录成文字并生成摘要，写回audio/audioRecord
block的transcript+summary字段。同analyze_image，不走interrupt()确认卡片，分析完
直接写入。转录用VolcengineAsrClient，摘要复用对话默认的文本模型（DeepSeek等）——
转录出来的是纯文字，不需要视觉/音频能力，普通chat模型就够。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.messages import HumanMessage
from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.asr_client import AsrClientError, VolcengineAsrClient
from app.config import settings
from app.java_client import JavaClient, JavaClientError
from app.message_content import extract_text_content
from app.models import resolve_chat_model
from app.state import AgentState

AUDIO_BLOCK_TYPES = ("audio", "audioRecord")
SUMMARY_PROMPT_PREFIX = (
    "下面是一段语音的转录文字，请整理成简洁的摘要和要点列表（用短句或要点符号），"
    "只输出摘要本身，不要加解释或前后缀：\n\n"
)


def make_transcribe_audio_tool(
    java_client: JavaClient, asr_client: VolcengineAsrClient, media_block_updates: list | None = None,
):
    @tool
    async def transcribe_audio(
        state: Annotated[AgentState, InjectedState], index: int | None = None
    ) -> str:
        """把用户当前笔记里一段音频/录音转录成文字并生成摘要，写入这段音频播放器下方
        （transcript逐字稿 + summary摘要）。当笔记里只有一段音频（audio或录音block都算）
        时不需要传index；有多段时，index是这段音频在笔记里从上到下的顺序号（从1开始，
        audio和audioRecord两种block统一编号）。不确定是第几段时不要瞎猜，留空index，
        工具会返回音频数量，你据此向用户确认。"""
        note_id = state.get("current_note_id")
        if not note_id:
            return "当前没有打开的笔记，无法转录音频。请先打开一篇笔记再试。"

        try:
            blocks = await java_client.list_media_blocks(note_id)
        except JavaClientError as e:
            return f"读取笔记里的音频列表失败：{e}"
        audios = [b for b in blocks if b.get("blockType") in AUDIO_BLOCK_TYPES]

        if not audios:
            return "这篇笔记里没有音频。"
        if len(audios) == 1:
            target = audios[0]
        elif index is not None and 1 <= index <= len(audios):
            target = audios[index - 1]
        else:
            return f"这篇笔记里有{len(audios)}段音频，请告诉我具体是第几段（从上到下数，从1开始）。"

        try:
            media = await java_client.get_block_media(note_id, target["blockId"])
        except JavaClientError as e:
            return f"读取音频文件失败：{e}"

        try:
            transcript = await asr_client.transcribe(media["base64Data"])
        except AsrClientError as e:
            return f"语音识别失败：{e}"
        if not transcript:
            return "这段音频没有识别出内容（可能是静音）。"

        model = resolve_chat_model(settings.default_provider)
        # 摘要失败不能连累已经花钱做完的转录：退回截断的逐字稿当摘要，保证 transcript 写得进去
        try:
            summary_response = await model.ainvoke([HumanMessage(SUMMARY_PROMPT_PREFIX + transcript)])
            summary = extract_text_content(summary_response.content).strip() or transcript[:200]
        except Exception:
            summary = transcript[:200]

        try:
            updated_data = await java_client.patch_media_block(
                note_id, target["blockId"], {"transcript": transcript, "summary": summary},
            )
        except JavaClientError as e:
            return f"已完成转录，但写回笔记失败：{e}（摘要：{summary}）"

        if media_block_updates is not None:
            media_block_updates.append({
                "noteId": note_id, "blockId": target["blockId"],
                "blockType": target["blockType"], "data": updated_data,
            })

        return f"已完成转录并生成摘要，写入笔记。摘要：{summary}"

    return transcribe_audio
