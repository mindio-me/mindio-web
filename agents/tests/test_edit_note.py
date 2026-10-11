"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from app.java_client import JavaClientError
from tools.content_cache import ContentCache
from tools.edit_note import make_note_edit_tools

VIEW = {"noteId": 9, "contentType": "editorjs", "title": "设计", "blocks": [
    {"id": "h", "type": "header", "hash": "11111111", "text": "## 方案", "editable": True},
    {"id": "a", "type": "audioRecord", "hash": "22222222", "text": "[音频]", "editable": False},
]}


class FakeJavaClient:
    def __init__(self, view=VIEW, result=None, error=None):
        self.view, self.error = view, error
        self.result = result or {"noteId": 9, "contentType": "editorjs", "turnId": "77", "ops": [{"op": "delete", "blockId": "a"}]}
        self.edit_calls = []

    async def get_note_blocks(self, username, note_id):
        return self.view

    async def edit_note(self, username, note_id, conversation_id, operations):
        self.edit_calls.append((username, note_id, conversation_id, operations))
        if self.error:
            raise self.error
        return self.result


def _state(current_note_id=9):
    return {"messages": [], "todos": [], "conversation_id": "5", "username": "alice",
            "current_note_context": None, "current_note_id": current_note_id,
            "current_project_context": None, "current_project_id": None}


async def test_read_note_blocks_lists_each_block_with_id_type_and_hash():
    read, _ = make_note_edit_tools(FakeJavaClient(), ContentCache())

    text = await read.ainvoke({"state": _state()})

    assert "《设计》" in text
    assert "[h | header | hash=11111111] ## 方案" in text
    assert "[a | audioRecord | hash=22222222]（只能删除/移动） [音频]" in text


async def test_read_note_blocks_not_found_and_no_note():
    read, _ = make_note_edit_tools(FakeJavaClient(view=None), ContentCache())

    assert "找不到" in await read.ainvoke({"note_id": 3, "state": _state()})
    assert "note_id" in await read.ainvoke({"state": _state(current_note_id=None)})


async def test_edit_note_passes_conversation_and_records_event():
    java, sink = FakeJavaClient(), []
    _, edit = make_note_edit_tools(java, ContentCache(), sink)
    ops = [{"op": "delete", "blockIds": ["a"], "hashes": ["22222222"]}]

    result = await edit.ainvoke({"operations": ops, "state": _state()})

    assert java.edit_calls == [("alice", 9, "5", ops)]
    assert sink == [{"noteId": 9, "data": java.result}]
    assert "已修改" in result and "read_note_blocks" in result


async def test_edit_note_error_is_friendly_and_records_nothing():
    sink = []
    _, edit = make_note_edit_tools(FakeJavaClient(error=JavaClientError("文档在你读取后被修改过（块 h）")),
                                   ContentCache(), sink)

    result = await edit.ainvoke({"operations": [{"op": "insert", "after": "end", "markdown": "x"}], "state": _state()})

    assert "修改笔记失败" in result and "块 h" in result
    assert sink == []


async def test_edit_note_requires_operations():
    _, edit = make_note_edit_tools(FakeJavaClient(), ContentCache())

    assert "operations" in await edit.ainvoke({"operations": [], "state": _state()})
