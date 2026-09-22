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

from app.config import Settings, settings as default_settings

VOLCENGINE_ASR_URL = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"
SUCCESS_STATUS_CODE = "20000000"


class AsrClientError(Exception):
    """语音识别调用失败（网络错误、非2xx响应、或X-Api-Status-Code不是成功码）。"""


class VolcengineAsrClient:
    def __init__(self, cfg: Settings = default_settings, client: httpx.AsyncClient | None = None):
        self._cfg = cfg
        self._client = client or httpx.AsyncClient(timeout=180.0)

    async def transcribe(self, audio_base64: str) -> str:
        # 新版"豆包语音"控制台签发的是单个 API Key，只认 X-Api-Key 这一个头；
        # 老控制台是 App ID + Access Token 两个头。两种鉴权模式互斥，混着发会
        # 导致服务端按老模式去查grant、查不到（“load grant: requested grant not
        # found”），跟key本身对不对无关。用 access_key 是否留空来区分走哪条。
        if self._cfg.volcengine_asr_access_key:
            headers = {
                "X-Api-App-Key": self._cfg.volcengine_asr_app_key,
                "X-Api-Access-Key": self._cfg.volcengine_asr_access_key,
            }
        else:
            headers = {"X-Api-Key": self._cfg.volcengine_asr_app_key}
        headers.update({
            "X-Api-Resource-Id": "volc.bigasr.auc_turbo",
            "X-Api-Request-Id": str(uuid.uuid4()),
            "X-Api-Sequence": "-1",
        })
        body = {
            "user": {"uid": self._cfg.volcengine_asr_app_key},
            "audio": {"data": audio_base64},
            "request": {"model_name": "bigmodel", "enable_itn": True, "enable_punc": True},
        }
        try:
            resp = await self._client.post(VOLCENGINE_ASR_URL, headers=headers, json=body)
        except httpx.HTTPError as e:
            raise AsrClientError(f"语音识别请求失败: {e}") from e

        status_code = resp.headers.get("X-Api-Status-Code", "")
        if status_code != SUCCESS_STATUS_CODE:
            message = resp.headers.get("X-Api-Message", "未知错误")
            raise AsrClientError(f"语音识别失败: status_code={status_code} message={message}")

        payload = resp.json()
        return payload.get("result", {}).get("text", "").strip()

    async def aclose(self) -> None:
        await self._client.aclose()


default_asr_client = VolcengineAsrClient()
