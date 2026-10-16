# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

import dataclasses

import httpx
import pytest

from app.asr_client import AsrClientError, AsrNotConfiguredError, VolcengineAsrClient


def _client_with_transport(cfg, handler) -> VolcengineAsrClient:
    transport = httpx.MockTransport(handler)
    async_client = httpx.AsyncClient(transport=transport)
    return VolcengineAsrClient(cfg=cfg, client=async_client)


async def test_transcribe_sends_expected_headers_and_body(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["headers"] = dict(request.headers)
        captured["body"] = request.content
        return httpx.Response(
            200,
            headers={"X-Api-Status-Code": "20000000", "X-Api-Message": "OK"},
            json={"result": {"text": "识别到的文字"}},
        )

    client = _client_with_transport(test_settings, handler)
    result = await client.transcribe("base64audio")

    assert captured["headers"]["x-api-app-key"] == "asr-app-key"
    assert captured["headers"]["x-api-access-key"] == "asr-access-key"
    assert captured["headers"]["x-api-resource-id"] == "volc.bigasr.auc_turbo"
    assert b'"data":"base64audio"' in captured["body"]
    assert b'"model_name":"bigmodel"' in captured["body"]
    assert result == "识别到的文字"


async def test_transcribe_uses_single_api_key_header_when_access_key_blank(test_settings):
    new_console_settings = dataclasses.replace(
        test_settings, volcengine_asr_app_key="new-console-api-key", volcengine_asr_access_key="",
    )
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["headers"] = dict(request.headers)
        return httpx.Response(
            200,
            headers={"X-Api-Status-Code": "20000000"},
            json={"result": {"text": "识别到的文字"}},
        )

    client = _client_with_transport(new_console_settings, handler)
    await client.transcribe("base64audio")

    assert captured["headers"]["x-api-key"] == "new-console-api-key"
    assert "x-api-app-key" not in captured["headers"]
    assert "x-api-access-key" not in captured["headers"]


async def test_transcribe_strips_result_text(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"X-Api-Status-Code": "20000000"},
            json={"result": {"text": "  有空白的文字  "}},
        )

    client = _client_with_transport(test_settings, handler)
    result = await client.transcribe("base64audio")

    assert result == "有空白的文字"


async def test_transcribe_raises_when_status_code_header_indicates_failure(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"X-Api-Status-Code": "45000151", "X-Api-Message": "音频格式错误".encode("utf-8")},
            json={},
        )

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(AsrClientError, match="45000151"):
        await client.transcribe("base64audio")


async def test_transcribe_wraps_network_errors(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("boom", request=request)

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(AsrClientError):
        await client.transcribe("base64audio")


async def test_transcribe_returns_empty_string_when_no_speech_detected(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"X-Api-Status-Code": "20000000"},
            json={"result": {"text": ""}},
        )

    client = _client_with_transport(test_settings, handler)
    result = await client.transcribe("base64audio")

    assert result == ""


async def test_transcribe_utterances_requests_utterances_and_maps_fields(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = request.content
        return httpx.Response(
            200,
            headers={"X-Api-Status-Code": "20000000"},
            json={"result": {"text": "你好。今天开会。", "utterances": [
                {"start_time": 0, "end_time": 1200, "text": "你好。", "words": []},
                {"start_time": 1500, "end_time": 3000, "text": " 今天开会。 "},
                {"start_time": 3100, "end_time": 3200, "text": "  "},
            ]}},
        )

    client = _client_with_transport(test_settings, handler)
    result = await client.transcribe_utterances("base64audio")

    assert b'"show_utterances":true' in captured["body"]
    assert result == [
        {"start": 0, "end": 1200, "text": "你好。"},
        {"start": 1500, "end": 3000, "text": "今天开会。"},
    ]


async def test_transcribe_utterances_tolerates_null_timestamps(test_settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"X-Api-Status-Code": "20000000"},
            json={"result": {"utterances": [{"start_time": None, "end_time": None, "text": "你好。"}]}},
        )

    client = _client_with_transport(test_settings, handler)
    assert await client.transcribe_utterances("x") == [{"start": 0, "end": 0, "text": "你好。"}]


async def test_transcribe_utterances_returns_empty_list_when_no_utterances(test_settings):
    def handler(request):
        return httpx.Response(200, headers={"X-Api-Status-Code": "20000000"}, json={"result": {"text": ""}})

    client = _client_with_transport(test_settings, handler)
    assert await client.transcribe_utterances("x") == []


async def test_transcribe_utterances_treats_silence_status_as_empty(test_settings):
    def handler(request):
        return httpx.Response(
            200,
            headers={"X-Api-Status-Code": "20000003", "X-Api-Message": "Normal silence audio"},
            json={},
        )

    client = _client_with_transport(test_settings, handler)
    assert await client.transcribe_utterances("x") == []
    assert await client.transcribe("x") == ""


async def test_transcribe_utterances_raises_not_configured_without_key(test_settings):
    cfg = dataclasses.replace(test_settings, volcengine_asr_app_key="", volcengine_asr_access_key="")

    def handler(request):
        raise AssertionError("不应发请求")

    client = _client_with_transport(cfg, handler)
    with pytest.raises(AsrNotConfiguredError):
        await client.transcribe_utterances("x")


async def test_transcribe_utterances_requests_speaker_info_and_maps_speaker(test_settings):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = request.content
        return httpx.Response(
            200,
            headers={"X-Api-Status-Code": "20000000"},
            json={"result": {"utterances": [
                {"start_time": 0, "end_time": 1000, "text": "你好。", "additions": {"speaker": "1"}},
                {"start_time": 1200, "end_time": 2000, "text": "Hi.", "additions": {"speaker": "2"}},
                {"start_time": 2100, "end_time": 2500, "text": "无编号。"},
                {"start_time": 2600, "end_time": 2900, "text": "怪编号。", "additions": {"speaker": "x"}},
            ]}},
        )

    client = _client_with_transport(test_settings, handler)
    result = await client.transcribe_utterances("base64audio")

    assert b'"enable_speaker_info":true' in captured["body"]
    assert result == [
        {"start": 0, "end": 1000, "text": "你好。", "speaker": 1},
        {"start": 1200, "end": 2000, "text": "Hi.", "speaker": 2},
        {"start": 2100, "end": 2500, "text": "无编号。"},
        {"start": 2600, "end": 2900, "text": "怪编号。"},
    ]


async def test_transcribe_utterances_retries_without_speaker_info_when_rejected(test_settings):
    bodies = []

    def handler(request: httpx.Request) -> httpx.Response:
        bodies.append(request.content)
        if len(bodies) == 1:
            return httpx.Response(200, headers={"X-Api-Status-Code": "45000001", "X-Api-Message": "bad param"})
        return httpx.Response(200, headers={"X-Api-Status-Code": "20000000"},
                              json={"result": {"utterances": [{"start_time": 0, "end_time": 900, "text": "好"}]}})

    client = _client_with_transport(test_settings, handler)
    result = await client.transcribe_utterances("base64audio")

    assert len(bodies) == 2
    assert b"enable_speaker_info" in bodies[0]
    assert b"enable_speaker_info" not in bodies[1]
    assert result == [{"start": 0, "end": 900, "text": "好"}]


async def test_transcribe_utterances_does_not_retry_network_errors(test_settings):
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(1)
        raise httpx.ConnectError("down")

    client = _client_with_transport(test_settings, handler)
    with pytest.raises(AsrClientError):
        await client.transcribe_utterances("base64audio")
    assert len(calls) == 1


def test_speaker_ignores_non_ascii_digits():
    from app.asr_client import _speaker

    assert _speaker("²") is None
    assert _speaker(" 2 ") == 2
    assert _speaker(None) is None
