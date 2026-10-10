# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""让agent把用户当前笔记里某段音频/录音交给后台转写：登记一次手动转写任务就返回，
不在对话里同步等结果。转写和整理由 Java 的 AudioTranscriptionJob 完成，结果显示在音频块下方。
详见 docs/superpowers/specs/2026-10-10-recording-auto-notes-design.md。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.java_client import JavaClient, JavaClientError
from app.state import AgentState

AUDIO_BLOCK_TYPES = ("audio", "audioRecord")


def make_transcribe_audio_tool(java_client: JavaClient, asr_client=None, media_block_updates: list | None = None):
    # asr_client 已不再使用（转写改由后台任务完成），保留参数免得改 graph 的装配代码

    @tool
    async def transcribe_audio(
        state: Annotated[AgentState, InjectedState], index: int | None = None
    ) -> str:
        """把用户当前笔记里一段音频/录音交给后台转写，完成后逐字稿、AI 优化版和结构化总结会
        显示在这段音频下方（通常需要几十秒到几分钟）。当笔记里只有一段音频（audio或录音block
        都算）时不需要传index；有多段时，index是这段音频在笔记里从上到下的顺序号（从1开始，
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
            record = await java_client.trigger_audio_transcription(note_id, target["blockId"])
        except JavaClientError as e:
            return f"启动转写失败：{e}"

        if isinstance(record, dict) and record.get("status") == "DONE":
            # 后端对已完成的结果不会重置；这里也不推 transcriptionQueued，免得面板误以为在排队
            return "这段录音已经转写过了，结果就在录音下方。如需重新转写，可以点录音下方的「重新转写」。"

        if media_block_updates is not None:
            media_block_updates.append({
                "noteId": note_id, "blockId": target["blockId"], "blockType": target["blockType"],
                "data": None, "transcriptionQueued": True,
            })
        return "已开始转写，完成后逐字稿和总结会显示在这段录音下方。"

    return transcribe_audio
