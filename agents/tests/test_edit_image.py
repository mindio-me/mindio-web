"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from app.java_client import JavaClientError
from tools.edit_image import make_edit_image_tools


class FakeJavaClient:
    def __init__(self, result=None, error=None):
        self.result = result or {"op": "insert", "noteId": 9, "contentType": "editorjs", "blockId": "b1"}
        self.error = error
        self.calls: list[tuple] = []

    async def insert_note_image(self, username, note_id, source, caption, anchor_text):
        self.calls.append(("insert", username, note_id, source, caption, anchor_text))
        if self.error:
            raise self.error
        return self.result

    async def delete_note_image(self, username, note_id, index):
        self.calls.append(("delete", username, note_id, index))
        return {"op": "delete", "noteId": note_id, "contentType": "markdown"}

    async def set_note_image_caption(self, username, note_id, index, caption):
        self.calls.append(("caption", username, note_id, index, caption))
        return {"op": "caption", "noteId": note_id, "contentType": "editorjs", "blockId": "b1"}


def _state(current_note_id=9, attachments=None) -> dict:
    return {
        "messages": [], "todos": [], "conversation_id": "alice", "username": "alice",
        "current_note_context": None, "current_note_id": current_note_id,
        "current_project_context": None, "current_project_id": None,
        "current_attachments": attachments,
    }


def _tools(java, sink=None):
    insert, delete, caption = make_edit_image_tools(java, sink)
    return insert, delete, caption


async def test_insert_from_other_note_defaults_to_current_note_and_records_event():
    java, sink = FakeJavaClient(), []
    insert, _, _ = _tools(java, sink)

    result = await insert.ainvoke({
        "source_note_id": 5, "source_index": 2, "caption": "图1", "anchor_text": "【配图位：图 1】",
        "state": _state(),
    })

    assert java.calls == [("insert", "alice", 9, {"type": "note", "id": 5, "index": 2}, "图1", "【配图位：图 1】")]
    assert sink == [{"noteId": 9, "data": java.result}]
    assert "已插入" in result


async def test_insert_from_clip_and_from_url():
    java = FakeJavaClient()
    insert, _, _ = _tools(java)

    await insert.ainvoke({"source_clip_id": 7, "source_index": 1, "target_note_id": 3, "state": _state()})
    await insert.ainvoke({"image_url": "https://example.com/a.png", "state": _state()})

    assert java.calls[0][2:4] == (3, {"type": "clip", "id": 7, "index": 1})
    assert java.calls[1][3] == {"type": "url", "url": "https://example.com/a.png"}


async def test_insert_from_attachment_uses_url_from_state_not_model():
    java = FakeJavaClient()
    insert, _, _ = _tools(java)
    attachments = [
        {"type": "document", "url": "/api/uploads/a.pdf", "fileName": "a.pdf"},
        {"type": "image", "url": "/api/uploads/chat/b.png", "fileName": "b.png"},
    ]

    await insert.ainvoke({"attachment_index": 1, "state": _state(attachments=attachments)})

    assert java.calls[0][3] == {"type": "attachment", "url": "/api/uploads/chat/b.png"}


async def test_insert_attachment_index_out_of_range():
    insert, _, _ = _tools(FakeJavaClient())

    result = await insert.ainvoke({"attachment_index": 1, "state": _state(attachments=None)})

    assert "只有0张图片附件" in result


async def test_insert_requires_exactly_one_source():
    java = FakeJavaClient()
    insert, _, _ = _tools(java)

    none_given = await insert.ainvoke({"state": _state()})
    two_given = await insert.ainvoke({"image_url": "https://x/a.png", "attachment_index": 1, "state": _state()})

    assert "只能给一种" in none_given and "只能给一种" in two_given
    assert java.calls == []


async def test_insert_from_note_requires_source_index():
    insert, _, _ = _tools(FakeJavaClient())

    result = await insert.ainvoke({"source_note_id": 5, "state": _state()})

    assert "source_index" in result


async def test_insert_without_target_and_without_current_note():
    insert, _, _ = _tools(FakeJavaClient())

    result = await insert.ainvoke({"image_url": "https://x/a.png", "state": _state(current_note_id=None)})

    assert "target_note_id" in result


async def test_insert_java_error_becomes_friendly_message_and_no_event():
    sink = []
    insert, _, _ = _tools(FakeJavaClient(error=JavaClientError("找不到包含「xx」的段落")), sink)

    result = await insert.ainvoke({"image_url": "https://x/a.png", "anchor_text": "xx", "state": _state()})

    assert "插入图片失败" in result and "找不到包含「xx」的段落" in result
    assert sink == []


async def test_delete_and_caption_use_current_note_by_default():
    java, sink = FakeJavaClient(), []
    _, delete, caption = _tools(java, sink)

    await delete.ainvoke({"index": 2, "state": _state()})
    await caption.ainvoke({"index": 1, "caption": "新", "note_id": 4, "state": _state()})

    assert java.calls == [("delete", "alice", 9, 2), ("caption", "alice", 4, 1, "新")]
    assert [e["noteId"] for e in sink] == [9, 4]
