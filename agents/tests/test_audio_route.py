"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
import dataclasses
import json

import pytest
from fastapi import HTTPException
from langchain_core.messages import AIMessage

import app.routes.audio as audio_route
from app.asr_client import AsrClientError, AsrNotConfiguredError
from app.java_client import JavaClientError
from app.routes.audio import AsrTranscribeRequest, AudioSummarizeRequest, asr_transcribe, audio_summarize


class FakeJava:
    def __init__(self, audio=b"abc", error=None):
        self.audio, self.error, self.calls = audio, error, []

    async def get_block_audio(self, note_id, block_id, segment=None):
        self.calls.append((note_id, block_id, segment))
        if self.error:
            raise self.error
        return self.audio

    async def aclose(self):
        pass


class FakeAsr:
    def __init__(self, utterances=None, error=None):
        self.utterances, self.error = utterances or [], error

    async def transcribe_utterances(self, audio_base64):
        if self.error:
            raise self.error
        return self.utterances

    async def aclose(self):
        pass


def _patch(monkeypatch, java, asr):
    monkeypatch.setattr(audio_route, "JavaClient", lambda: java)
    monkeypatch.setattr(audio_route, "VolcengineAsrClient", lambda: asr)


async def test_asr_transcribe_adds_offset(monkeypatch):
    java = FakeJava()
    _patch(monkeypatch, java, FakeAsr([{"start": 0, "end": 1000, "text": "你好"}]))
    resp = await asr_transcribe(AsrTranscribeRequest(noteId=1, blockId="b", segment=2, offsetMs=3_600_000))
    assert resp.utterances == [{"start": 3_600_000, "end": 3_601_000, "text": "你好"}]
    assert java.calls == [(1, "b", 2)]


@pytest.mark.parametrize("asr_error,code,status", [
    (AsrNotConfiguredError("未配置"), "ASR_NOT_CONFIGURED", 422),
    (AsrClientError("超时"), "ASR_FAILED", 502),
])
async def test_asr_transcribe_maps_asr_errors(monkeypatch, asr_error, code, status):
    _patch(monkeypatch, FakeJava(), FakeAsr(error=asr_error))
    with pytest.raises(HTTPException) as e:
        await asr_transcribe(AsrTranscribeRequest(noteId=1, blockId="b", segment=None, offsetMs=0))
    assert e.value.status_code == status and e.value.detail["code"] == code


async def test_asr_transcribe_without_key_fails_before_fetching_audio(monkeypatch, test_settings):
    async def no_key(java_client, base=None):
        return dataclasses.replace(test_settings, volcengine_asr_app_key="", volcengine_asr_access_key="")

    monkeypatch.setattr(audio_route, "load_effective_settings", no_key)
    java = FakeJava()
    _patch(monkeypatch, java, FakeAsr())
    with pytest.raises(HTTPException) as e:
        await asr_transcribe(AsrTranscribeRequest(noteId=1, blockId="b", segment=None, offsetMs=0))
    assert e.value.status_code == 422 and e.value.detail["code"] == "ASR_NOT_CONFIGURED"
    assert java.calls == []


async def test_asr_transcribe_maps_fetch_error_and_size(monkeypatch):
    _patch(monkeypatch, FakeJava(error=JavaClientError("404")), FakeAsr())
    with pytest.raises(HTTPException) as e:
        await asr_transcribe(AsrTranscribeRequest(noteId=1, blockId="b", segment=None, offsetMs=0))
    assert e.value.detail["code"] == "AUDIO_FETCH_FAILED"

    monkeypatch.setattr(audio_route, "ASR_MAX_BYTES", 2)
    _patch(monkeypatch, FakeJava(audio=b"abc"), FakeAsr())
    with pytest.raises(HTTPException) as e:
        await asr_transcribe(AsrTranscribeRequest(noteId=1, blockId="b", segment=None, offsetMs=0))
    assert e.value.status_code == 413 and e.value.detail["code"] == "AUDIO_TOO_LARGE"


UTTS = [
    {"start": 0, "end": 4000, "text": "大家好嗯。"},
    {"start": 5000, "end": 9000, "text": "今天讲三件事。"},
]


class ScriptedModel:
    """按 prompt 里的标识返回预设内容：总结 prompt 含 SUMMARY_TAG，优化 prompt 含 POLISH_TAG。"""
    def __init__(self, summary, polish, polish_error=False):
        self.summary, self.polish, self.polish_error = summary, polish, polish_error
        self.prompts = []

    async def ainvoke(self, messages):
        text = messages[0].content
        self.prompts.append(text)
        if audio_route.SUMMARY_TAG in text:
            return AIMessage(content=self.summary)
        if self.polish_error:
            raise RuntimeError("模型挂了")
        return AIMessage(content=self.polish)


async def test_audio_summarize_returns_polished_and_resolved_summary(monkeypatch):
    summary = json.dumps({
        "overview": "分享", "keyPoints": [], "chapters": [{"index": 1, "title": "正题", "desc": ""}],
        "quotes": [], "todos": [], "highlights": [{"mark": 0, "text": "开场"}],
    }, ensure_ascii=False)
    model = ScriptedModel(summary, "[#0] 大家好。\n[#1] 今天讲三件事。")
    monkeypatch.setattr(audio_route, "resolve_chat_model", lambda provider: model)

    resp = await audio_summarize(AudioSummarizeRequest(utterances=UTTS, marks=[3000]))

    assert resp.polished == [{"start": 0, "text": "大家好。"}, {"start": 5000, "text": "今天讲三件事。"}]
    assert resp.summary["chapters"] == [{"start": 5000, "title": "正题", "desc": ""}]
    assert resp.summary["highlights"] == [{"markAt": 3000, "start": 0, "end": 9000, "text": "开场"}]
    summary_prompt = next(p for p in model.prompts if audio_route.SUMMARY_TAG in p)
    assert "[#0 00:00] 大家好嗯。" in summary_prompt and "标记 0" in summary_prompt


async def test_audio_summarize_falls_back_to_raw_when_polish_fails(monkeypatch):
    model = ScriptedModel('{"overview": "x"}', "", polish_error=True)
    monkeypatch.setattr(audio_route, "resolve_chat_model", lambda provider: model)
    resp = await audio_summarize(AudioSummarizeRequest(utterances=UTTS, marks=[]))
    assert resp.polished == [{"start": 0, "text": "大家好嗯。今天讲三件事。"}]


async def test_audio_summarize_raises_summary_failed_on_bad_json(monkeypatch):
    model = ScriptedModel("不是json", "[#0] 甲")
    monkeypatch.setattr(audio_route, "resolve_chat_model", lambda provider: model)
    with pytest.raises(HTTPException) as e:
        await audio_summarize(AudioSummarizeRequest(utterances=UTTS, marks=[]))
    assert e.value.status_code == 502 and e.value.detail["code"] == "SUMMARY_FAILED"


async def test_asr_transcribe_keeps_speaker(monkeypatch):
    _patch(monkeypatch, FakeJava(), FakeAsr([{"start": 0, "end": 1000, "text": "你好", "speaker": 2}]))
    resp = await asr_transcribe(AsrTranscribeRequest(noteId=1, blockId="b", segment=None, offsetMs=500))
    assert resp.utterances == [{"start": 500, "end": 1500, "text": "你好", "speaker": 2}]
