# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""调用火山引擎"大模型录音文件极速版识别"API（豆包语音）做语音转文字。这是同步接口，
一次POST请求直接拿到完整识别结果，不需要像老版"录音文件识别标准版"那样提交任务再轮询。
状态码在响应头X-Api-Status-Code里（不是响应体），20000000表示成功——这是文档里明确写的、
容易和"body里带code字段"的常见约定搞混的地方，参考:
https://docs.volcengine.com/docs/6561/1631584?lang=zh
"""
from __future__ import annotations

import uuid

import httpx

from app.config import Settings, current_settings

VOLCENGINE_ASR_URL = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"
SUCCESS_STATUS_CODE = "20000000"
# 全静音音频：服务端不返回 utterances，按"成功但无内容"处理
SILENCE_STATUS_CODE = "20000003"


class AsrClientError(Exception):
    """语音识别调用失败（网络错误、非2xx响应、或X-Api-Status-Code不是成功码）。"""


class AsrNotConfiguredError(AsrClientError):
    """没有配置语音识别 Key：设置页和 agents 环境变量都是空的。"""


class AsrStatusError(AsrClientError):
    """接口返回了非成功状态码（区别于网络错误）：带说话人参数时据此去参重试一次。"""


def _speaker(value) -> int | None:
    s = str(value or "").strip()
    # isdigit() 对上标数字 "²" 等也返回 True，但 int() 解析不了
    try:
        return int(s) if s.isdigit() else None
    except ValueError:
        return None


class VolcengineAsrClient:
    def __init__(self, cfg: Settings | None = None, client: httpx.AsyncClient | None = None):
        self._cfg = cfg
        self._client = client or httpx.AsyncClient(timeout=180.0)

    async def _recognize(self, audio_base64: str, show_utterances: bool, speaker_info: bool = False) -> dict:
        cfg = self._cfg or current_settings()
        if not cfg.volcengine_asr_app_key:
            raise AsrNotConfiguredError("请到 设置 → AI 服务 填写语音识别 Key")
        # 新版"豆包语音"控制台签发的是单个 API Key，只认 X-Api-Key 这一个头；
        # 老控制台是 App ID + Access Token 两个头。两种鉴权模式互斥，混着发会
        # 导致服务端按老模式去查grant、查不到（“load grant: requested grant not
        # found”），跟key本身对不对无关。用 access_key 是否留空来区分走哪条。
        if cfg.volcengine_asr_access_key:
            headers = {
                "X-Api-App-Key": cfg.volcengine_asr_app_key,
                "X-Api-Access-Key": cfg.volcengine_asr_access_key,
            }
        else:
            headers = {"X-Api-Key": cfg.volcengine_asr_app_key}
        headers.update({
            "X-Api-Resource-Id": "volc.bigasr.auc_turbo",
            "X-Api-Request-Id": str(uuid.uuid4()),
            "X-Api-Sequence": "-1",
        })
        request_opts = {"model_name": "bigmodel", "enable_itn": True, "enable_punc": True}
        if show_utterances:
            request_opts["show_utterances"] = True
        if speaker_info:
            request_opts["enable_speaker_info"] = True
        body = {
            "user": {"uid": cfg.volcengine_asr_app_key},
            "audio": {"data": audio_base64},
            "request": request_opts,
        }
        try:
            resp = await self._client.post(VOLCENGINE_ASR_URL, headers=headers, json=body)
        except httpx.HTTPError as e:
            raise AsrClientError(f"语音识别请求失败: {e}") from e

        status_code = resp.headers.get("X-Api-Status-Code", "")
        if status_code == SILENCE_STATUS_CODE:
            return {}
        if status_code != SUCCESS_STATUS_CODE:
            message = resp.headers.get("X-Api-Message", "未知错误")
            raise AsrStatusError(f"语音识别失败: status_code={status_code} message={message}")

        return resp.json()

    async def transcribe(self, audio_base64: str) -> str:
        payload = await self._recognize(audio_base64, show_utterances=False)
        return payload.get("result", {}).get("text", "").strip()

    async def transcribe_utterances(self, audio_base64: str) -> list[dict]:
        """逐句结果，时间是这段音频内的毫秒（调用方自己加分段偏移）；空文本句丢掉。
        开启说话人分离（实测字段在 utterances[].additions.speaker，值是字符串）；
        接口若拒绝该参数（非成功状态码），去掉参数重试一次。"""
        try:
            payload = await self._recognize(audio_base64, show_utterances=True, speaker_info=True)
        except AsrStatusError:
            payload = await self._recognize(audio_base64, show_utterances=True)
        result = []
        for u in payload.get("result", {}).get("utterances") or []:
            text = (u.get("text") or "").strip()
            if not text:
                continue
            item = {"start": int(u.get("start_time") or 0), "end": int(u.get("end_time") or 0), "text": text}
            speaker = _speaker((u.get("additions") or {}).get("speaker"))
            if speaker is not None:
                item["speaker"] = speaker
            result.append(item)
        return result

    async def aclose(self) -> None:
        await self._client.aclose()


default_asr_client = VolcengineAsrClient()
