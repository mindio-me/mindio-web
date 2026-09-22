# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import InMemorySaver

from app.asr_client import AsrClientError
from app.graph import build_graph_builder
from tools.transcribe_audio import make_transcribe_audio_tool


class FakeJavaClient:
    def __init__(self, media_blocks=None, media=None):
        self.media_blocks = media_blocks or []
        self.media = media or {"mimeType": "audio/mpeg", "base64Data": "abc"}
        self.patched: list[tuple] = []

    async def list_media_blocks(self, note_id):
        return self.media_blocks

    async def get_block_media(self, note_id, block_id):
        return self.media

    async def patch_media_block(self, note_id, block_id, fields):
        self.patched.append((note_id, block_id, fields))
        return {"url": "a.mp3", "duration": 30, **fields}


class FakeAsrClient:
    def __init__(self, text="今天开会讨论了三件事", error=None):
        self._text = text
        self._error = error

    async def transcribe(self, audio_base64: str) -> str:
        if self._error:
            raise self._error
        return self._text


class FakeSummaryModel:
    def __init__(self, content):
        self._content = content

    async def ainvoke(self, messages):
        return AIMessage(content=self._content)


class FailingSummaryModel:
    async def ainvoke(self, messages):
        raise RuntimeError("summary model exploded")


def _agent_input(text: str, current_note_id: int | None = None) -> dict:
    return {
        "messages": [HumanMessage(text)], "todos": [], "conversation_id": "alice",
        "username": "alice", "current_note_context": None, "current_note_id": current_note_id,
    }


def _run_config() -> dict:
    return {"configurable": {"thread_id": "alice"}}


async def test_transcribe_audio_single_audio_writes_transcript_and_summary(monkeypatch):
    import tools.transcribe_audio as transcribe_audio_route
    monkeypatch.setattr(
        transcribe_audio_route, "resolve_chat_model",
        lambda provider: FakeSummaryModel("会议纪要：三件事"),
    )
    java = FakeJavaClient(media_blocks=[{"blockId": "b1", "blockType": "audio", "url": "a.mp3"}])
    asr = FakeAsrClient()
    media_updates: list = []
    tool_ = make_transcribe_audio_tool(java, asr, media_updates)
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "transcribe_audio", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="已经转录好了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    await graph.ainvoke(_agent_input("转录一下这段录音", current_note_id=9), config=_run_config())

    assert java.patched == [(9, "b1", {
        "transcript": "今天开会讨论了三件事", "summary": "会议纪要：三件事",
    })]
    assert media_updates == [{
        "noteId": 9, "blockId": "b1", "blockType": "audio",
        "data": {"url": "a.mp3", "duration": 30, "transcript": "今天开会讨论了三件事", "summary": "会议纪要：三件事"},
    }]


async def test_transcribe_audio_summary_model_failure_falls_back_to_truncated_transcript(monkeypatch):
    import tools.transcribe_audio as transcribe_audio_route
    monkeypatch.setattr(
        transcribe_audio_route, "resolve_chat_model", lambda provider: FailingSummaryModel(),
    )
    java = FakeJavaClient(media_blocks=[{"blockId": "b1", "blockType": "audio", "url": "a.mp3"}])
    transcript = "今天开会讨论了三件事"
    asr = FakeAsrClient(text=transcript)
    tool_ = make_transcribe_audio_tool(java, asr)
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "transcribe_audio", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="已经转录好了")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("转录一下这段录音", current_note_id=9), config=_run_config())

    # 摘要模型挂了也不能丢掉已经付费做完的转录
    assert java.patched == [(9, "b1", {"transcript": transcript, "summary": transcript[:200]})]
    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "已完成转录" in tool_messages[0].content
    assert "失败" not in tool_messages[0].content


async def test_transcribe_audio_no_current_note_returns_message_without_writing():
    java = FakeJavaClient()
    tool_ = make_transcribe_audio_tool(java, FakeAsrClient())
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "transcribe_audio", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("转录一下", current_note_id=None), config=_run_config())

    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "没有打开的笔记" in tool_messages[0].content
    assert java.patched == []


async def test_transcribe_audio_no_audio_in_note_returns_message():
    java = FakeJavaClient(media_blocks=[{"blockId": "b1", "blockType": "image", "url": "a.png"}])
    tool_ = make_transcribe_audio_tool(java, FakeAsrClient())
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "transcribe_audio", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("转录一下", current_note_id=9), config=_run_config())

    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "没有音频" in tool_messages[0].content


async def test_transcribe_audio_counts_audio_and_audioRecord_blocks_together():
    java = FakeJavaClient(media_blocks=[
        {"blockId": "b1", "blockType": "audio", "url": "a.mp3"},
        {"blockId": "b2", "blockType": "audioRecord", "url": "b.webm"},
    ])
    tool_ = make_transcribe_audio_tool(java, FakeAsrClient())
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "transcribe_audio", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="好的")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("转录一下", current_note_id=9), config=_run_config())

    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "2段音频" in tool_messages[0].content
    assert java.patched == []


async def test_transcribe_audio_asr_failure_returns_error_message_without_writing():
    java = FakeJavaClient(media_blocks=[{"blockId": "b1", "blockType": "audio", "url": "a.mp3"}])
    asr = FakeAsrClient(error=AsrClientError("音频格式错误"))
    tool_ = make_transcribe_audio_tool(java, asr)
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "transcribe_audio", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="抱歉，没能转录")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("转录一下", current_note_id=9), config=_run_config())

    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "语音识别失败" in tool_messages[0].content
    assert java.patched == []


async def test_transcribe_audio_silent_audio_returns_message_without_writing():
    java = FakeJavaClient(media_blocks=[{"blockId": "b1", "blockType": "audio", "url": "a.mp3"}])
    asr = FakeAsrClient(text="")
    tool_ = make_transcribe_audio_tool(java, asr)
    tool_call_msg = AIMessage(content="", tool_calls=[{"name": "transcribe_audio", "args": {}, "id": "c1"}])
    final_msg = AIMessage(content="没听到内容")
    model = FakeMessagesListChatModel(responses=[tool_call_msg, final_msg])
    graph = build_graph_builder(model, [tool_]).compile(checkpointer=InMemorySaver())

    result = await graph.ainvoke(_agent_input("转录一下", current_note_id=9), config=_run_config())

    tool_messages = [m for m in result["messages"] if getattr(m, "type", None) == "tool"]
    assert "静音" in tool_messages[0].content
    assert java.patched == []
