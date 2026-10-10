# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import InMemorySaver

from app.graph import build_graph_builder
from app.java_client import JavaClientError
from tools.transcribe_audio import make_transcribe_audio_tool


class FakeJavaClient:
    def __init__(self, media_blocks=None, trigger_error=None):
        self.media_blocks = media_blocks or []
        self.trigger_error = trigger_error
        self.triggered: list[tuple] = []

    async def list_media_blocks(self, note_id):
        return self.media_blocks

    async def trigger_audio_transcription(self, note_id, block_id):
        if self.trigger_error:
            raise self.trigger_error
        self.triggered.append((note_id, block_id))
        return {"blockId": block_id, "status": "PENDING"}


def _agent_input(text: str, current_note_id: int | None = None) -> dict:
    return {
        "messages": [HumanMessage(text)], "todos": [], "conversation_id": "alice",
        "username": "alice", "current_note_context": None, "current_note_id": current_note_id,
        "current_project_context": None, "current_project_id": None,
    }


async def _run(tool_, current_note_id=None, **args) -> str:
    """经真实图调用工具（InjectedState只有走图才会注入），返回工具消息内容"""
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "transcribe_audio", "args": args, "id": "c1"}])
    model = FakeMessagesListChatModel(responses=[tool_call_msg, AIMessage(content="好的")])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())
    result = await graph.ainvoke(
        _agent_input("转录一下这段录音", current_note_id=current_note_id),
        config={"configurable": {"thread_id": "alice"}},
    )
    return [m for m in result["messages"] if getattr(m, "type", None) == "tool"][0].content


async def test_triggers_single_audio_and_reports_queued():
    java = FakeJavaClient([{"blockId": "r1", "blockType": "audioRecord"}])
    updates: list = []
    tool_ = make_transcribe_audio_tool(java, None, updates)
    result = await _run(tool_, 5)
    assert java.triggered == [(5, "r1")]
    assert "已开始转写" in result
    assert updates == [{"noteId": 5, "blockId": "r1", "blockType": "audioRecord", "data": None, "transcriptionQueued": True}]


async def test_asks_for_index_when_multiple_audios():
    java = FakeJavaClient([{"blockId": "a", "blockType": "audio"}, {"blockId": "b", "blockType": "audioRecord"}])
    tool_ = make_transcribe_audio_tool(java, None, [])
    assert "2段音频" in await _run(tool_, 5)
    assert java.triggered == []
    await _run(tool_, 5, index=2)
    assert java.triggered == [(5, "b")]


async def test_reports_no_note_and_no_audio_and_trigger_error():
    tool_ = make_transcribe_audio_tool(FakeJavaClient(), None, [])
    assert "没有打开的笔记" in await _run(tool_)
    assert "没有音频" in await _run(tool_, 5)
    failing = FakeJavaClient([{"blockId": "r1", "blockType": "audioRecord"}], trigger_error=JavaClientError("500"))
    updates: list = []
    assert "启动转写失败" in await _run(make_transcribe_audio_tool(failing, None, updates), 5)
    assert updates == []
