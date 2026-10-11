"""往笔记正文里插入图片、删除图片、写/改图注。三个工具都直接生效、不走 interrupt() 确认卡片
（用户明确选择的交互方式，见 docs/superpowers/specs/2026-10-11-agent-image-editing-design.md）。

写入成功后把 Java 返回的事件载荷放进 note_content_updates，由聊天路由转成
note_content_updated 流式事件——前端据此同步已经打开的编辑器，否则编辑器下一次
自动保存（整篇写回）会把这次改动覆盖掉。

聊天附件的地址不让模型自己填：模型只说"第几个附件"，地址从 state.current_attachments
里取（同 current_note_id 的套路），避免模型拼出任意地址。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.java_client import JavaClient
from app.state import AgentState


def make_edit_image_tools(java_client: JavaClient, note_content_updates: list | None = None) -> list:
    def _record(note_id: int, payload: dict) -> None:
        if note_content_updates is not None:
            note_content_updates.append({"noteId": note_id, "data": payload})

    @tool
    async def insert_image(
        state: Annotated[AgentState, InjectedState],
        caption: str = "",
        anchor_text: str | None = None,
        target_note_id: int | None = None,
        source_note_id: int | None = None,
        source_clip_id: int | None = None,
        source_index: int | None = None,
        attachment_index: int | None = None,
        image_url: str | None = None,
    ) -> str:
        """往笔记正文里插入一张图片（直接生效）。用户要求"把图放进笔记/配上图"时调用，不要说做不到。

        图片来源四选一：
        - source_note_id 或 source_clip_id + source_index：某篇笔记/收藏里的第几张图（序号同 view_image 列出的）
        - attachment_index：用户这条消息附带的第几张图片（从1开始）
        - image_url：网上的图片地址（会先下载保存再插入）
        target_note_id 不传时插入当前正在编辑的笔记。
        anchor_text：插入位置。正文里某一段的文字恰好等于它时（比如整段就是"【配图位：图 1】"），
        用图片替换这段；某段包含它时插在那段后面；不传则插到文末。
        caption：图注，会显示在图片下方。一次插多张就逐张调用。"""
        note_id = target_note_id or state.get("current_note_id")
        if not note_id:
            return "需要提供 target_note_id（当前也没有正在编辑的笔记）。"

        given = [s for s in (source_note_id, source_clip_id, attachment_index, image_url) if s is not None]
        if len(given) != 1:
            return ("图片来源需要且只能给一种：source_note_id/source_clip_id（配合 source_index）、"
                    "attachment_index、image_url。")

        if source_note_id is not None or source_clip_id is not None:
            if source_index is None:
                return "从笔记/收藏取图时需要 source_index（第几张，从1开始）。"
            if source_note_id is not None:
                source = {"type": "note", "id": source_note_id, "index": source_index}
            else:
                source = {"type": "clip", "id": source_clip_id, "index": source_index}
        elif attachment_index is not None:
            images = [a for a in (state.get("current_attachments") or []) if a.get("type") == "image" and a.get("url")]
            if not 1 <= attachment_index <= len(images):
                return f"本条消息里只有{len(images)}张图片附件，没有第{attachment_index}张。"
            source = {"type": "attachment", "url": images[attachment_index - 1]["url"]}
        else:
            source = {"type": "url", "url": image_url}

        try:
            result = await java_client.insert_note_image(state["username"], note_id, source, caption, anchor_text)
        except Exception as e:
            return f"插入图片失败：{e}"
        _record(note_id, result)
        position = "替换了锚点段落" if result.get("replacedBlockId") else ("插在锚点段落后" if anchor_text else "插在文末")
        return f"已插入图片到笔记 id={note_id}（{position}）" + (f"，图注：{caption}" if caption else "") + "。"

    @tool
    async def delete_image(
        index: int, state: Annotated[AgentState, InjectedState], note_id: int | None = None,
    ) -> str:
        """删除笔记正文里的第 index 张图片（从上到下从1开始，序号同 view_image 列出的；直接生效）。
        note_id 不传时删当前正在编辑的笔记里的图。图下方的图注会一起删掉。"""
        target = note_id or state.get("current_note_id")
        if not target:
            return "需要提供 note_id（当前也没有正在编辑的笔记）。"
        try:
            result = await java_client.delete_note_image(state["username"], target, index)
        except Exception as e:
            return f"删除图片失败：{e}"
        _record(target, result)
        return f"已删除笔记 id={target} 里的第{index}张图片。"

    @tool
    async def set_image_caption(
        index: int, caption: str, state: Annotated[AgentState, InjectedState], note_id: int | None = None,
    ) -> str:
        """给笔记正文里的第 index 张图片写或改图注（显示在图片下方；直接生效）。caption 传空字符串
        表示删掉图注。note_id 不传时改当前正在编辑的笔记。"""
        target = note_id or state.get("current_note_id")
        if not target:
            return "需要提供 note_id（当前也没有正在编辑的笔记）。"
        try:
            result = await java_client.set_note_image_caption(state["username"], target, index, caption)
        except Exception as e:
            return f"修改图注失败：{e}"
        _record(target, result)
        return f"已更新笔记 id={target} 第{index}张图片的图注。"

    return [insert_image, delete_image, set_image_caption]
