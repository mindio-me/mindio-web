"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from langchain_core.messages import AIMessage

import tools.view_image as view_image_module
from app.java_client import JavaClientError
from tools.view_image import make_view_image_tool


class FakeJavaClient:
    def __init__(self, images=None, media=None):
        self.images = images
        self.media = media or {"mimeType": "image/png", "base64Data": "abc"}
        self.list_calls: list[tuple] = []
        self.get_calls: list[tuple] = []

    async def list_document_images(self, username, kind, doc_id):
        self.list_calls.append((username, kind, doc_id))
        return self.images

    async def get_document_image(self, username, kind, doc_id, index):
        self.get_calls.append((username, kind, doc_id, index))
        return self.media


class RecordingModel:
    def __init__(self, answer="图里是短信网关架构：App → 网关 → 运营商"):
        self.answer = answer
        self.messages = None

    async def ainvoke(self, messages):
        self.messages = messages
        return AIMessage(content=self.answer)


def _state(current_note_id=None) -> dict:
    return {
        "messages": [], "todos": [], "conversation_id": "alice", "username": "alice",
        "current_note_context": None, "current_note_id": current_note_id,
        "current_project_context": None, "current_project_id": None,
    }


def _patch_model(monkeypatch, model):
    monkeypatch.setattr(view_image_module, "resolve_chat_model", lambda provider: model)


async def test_view_image_single_image_sends_question_and_base64_to_model(monkeypatch):
    model = RecordingModel()
    _patch_model(monkeypatch, model)
    java = FakeJavaClient(images=[{"index": 1, "url": "/uploads/a.png", "caption": ""}])
    tool_ = make_view_image_tool(java)

    result = await tool_.ainvoke({"question": "网关和运营商怎么连", "note_id": 12, "state": _state()})

    assert "短信网关架构" in result
    assert java.get_calls == [("alice", "notes", 12, 1)]
    content = model.messages[0].content
    assert "网关和运营商怎么连" in content[0]["text"]
    assert content[1]["image_url"]["url"] == "data:image/png;base64,abc"


async def test_view_image_external_url_passed_through_to_model(monkeypatch):
    model = RecordingModel()
    _patch_model(monkeypatch, model)
    java = FakeJavaClient(
        images=[{"index": 1, "url": "https://example.com/a.png", "caption": ""}],
        media={"url": "https://example.com/a.png"},
    )
    tool_ = make_view_image_tool(java)

    await tool_.ainvoke({"question": "这是什么", "clip_id": 7, "state": _state()})

    assert java.list_calls == [("alice", "clips", 7)]
    assert model.messages[0].content[1]["image_url"]["url"] == "https://example.com/a.png"


async def test_view_image_defaults_to_current_note(monkeypatch):
    _patch_model(monkeypatch, RecordingModel())
    java = FakeJavaClient(images=[{"index": 1, "url": "/uploads/a.png", "caption": ""}])
    tool_ = make_view_image_tool(java)

    await tool_.ainvoke({"question": "q", "state": _state(current_note_id=9)})

    assert java.list_calls == [("alice", "notes", 9)]


async def test_view_image_multiple_images_without_index_lists_them_without_calling_model(monkeypatch):
    model = RecordingModel()
    _patch_model(monkeypatch, model)
    java = FakeJavaClient(images=[
        {"index": 1, "url": "/uploads/a.png", "caption": "架构图"},
        {"index": 2, "url": "/uploads/b.png", "caption": ""},
    ])
    tool_ = make_view_image_tool(java)

    result = await tool_.ainvoke({"question": "q", "note_id": 12, "state": _state()})

    assert "2张图片" in result and "第1张：架构图" in result
    assert java.get_calls == []
    assert model.messages is None


async def test_view_image_with_index_picks_that_image(monkeypatch):
    _patch_model(monkeypatch, RecordingModel())
    java = FakeJavaClient(images=[
        {"index": 1, "url": "/uploads/a.png", "caption": ""},
        {"index": 2, "url": "/uploads/b.png", "caption": ""},
    ])
    tool_ = make_view_image_tool(java)

    result = await tool_.ainvoke({"question": "q", "note_id": 12, "index": 2, "state": _state()})

    assert java.get_calls == [("alice", "notes", 12, 2)]
    assert "第2/2张" in result


async def test_view_image_no_document_id_and_no_current_note():
    tool_ = make_view_image_tool(FakeJavaClient(images=[]))

    result = await tool_.ainvoke({"question": "q", "state": _state()})

    assert "note_id 或 clip_id" in result


async def test_view_image_not_found_or_not_owned():
    tool_ = make_view_image_tool(FakeJavaClient(images=None))

    result = await tool_.ainvoke({"question": "q", "note_id": 99, "state": _state()})

    assert "找不到" in result


async def test_view_image_no_images():
    tool_ = make_view_image_tool(FakeJavaClient(images=[]))

    result = await tool_.ainvoke({"question": "q", "note_id": 12, "state": _state()})

    assert "没有可查看的图片" in result


async def test_view_image_java_error_returns_friendly_message():
    class RaisingJavaClient:
        async def list_document_images(self, username, kind, doc_id):
            raise JavaClientError("boom")

    tool_ = make_view_image_tool(RaisingJavaClient())

    result = await tool_.ainvoke({"question": "q", "note_id": 12, "state": _state()})

    assert "读取图片列表失败" in result


async def test_view_image_model_failure_returns_friendly_message(monkeypatch):
    class FailingModel:
        async def ainvoke(self, messages):
            raise RuntimeError("model does not support images")

    _patch_model(monkeypatch, FailingModel())
    java = FakeJavaClient(images=[{"index": 1, "url": "/uploads/a.png", "caption": ""}])
    tool_ = make_view_image_tool(java)

    result = await tool_.ainvoke({"question": "q", "note_id": 12, "state": _state()})

    assert "看图失败" in result
