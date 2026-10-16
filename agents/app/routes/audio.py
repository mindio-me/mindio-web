"""
录音自动成笔记的两个无状态内部接口，供 Java 的 AudioTranscriptionJob 调用，和 agent 对话无关：
- /internal/asr-transcribe：转写一段音频（Python 自己从 Java 取音频字节），返回带偏移的逐句结果
- /internal/audio-summarize：根据逐句原文 + 标记生成结构化总结和 AI 优化版
详见 docs/superpowers/specs/2026-10-10-recording-auto-notes-design.md。
"""
from __future__ import annotations

import asyncio
import base64

from fastapi import APIRouter, HTTPException
from langchain_core.messages import HumanMessage
from pydantic import BaseModel

from app.asr_client import AsrClientError, AsrNotConfiguredError, VolcengineAsrClient
from app.audio_notes import (
    chunk_indices, extract_json, fallback_polished, fmt_ts, format_indexed,
    has_multiple_speakers, mark_windows, normalize_scene, parse_polished, resolve_summary,
)
from app.config import use_settings
from app.effective_settings import load_effective_settings
from app.java_client import JavaClient, JavaClientError
from app.message_content import extract_text_content
from app.models import resolve_chat_model

router = APIRouter()

# 火山极速版单次上限 100MB（录音按 30 分钟分段，正常远低于此）
ASR_MAX_BYTES = 100 * 1024 * 1024
POLISH_CONCURRENCY = 3
SUMMARY_TAG = "【录音结构化总结】"
POLISH_TAG = "【录音原文优化】"

SUMMARY_PROMPT = SUMMARY_TAG + """
下面是一段录音的逐句转写，每行格式为 [#序号 时间] 原文。请输出一个 JSON 对象（只输出 JSON，不要任何解释），结构如下：
{
  "overview": "一句话概述这段录音",
  "keyPoints": [{"topic": "主题", "points": ["**关键词**：展开说明"]}],
  "chapters": [{"index": 章节第一句的序号, "title": "章节标题", "desc": "一两句说明"}],
  "quotes": [{"text": "值得摘录的原话", "category": "分类，如 战略洞见/方法技巧", "index": 原话所在句的序号}],
  "todos": ["待办事项，没有就给空数组"],
  "highlights": [{"mark": 标记编号, "text": "一句话概括该标记对应的内容"}]
}
要求：序号必须来自原文里出现过的 [#序号]；章节按时间顺序；没有内容的字段给空数组。
"""

POLISH_PROMPT = POLISH_TAG + """
下面是录音逐句转写的一部分，每行格式为 [#序号 时间] 原文。请整理成通顺的书面文字：去掉口头语（嗯、啊、那个）、重复和明显的识别错误，不增删事实、不总结、不改变说话人的意思。
按意思分段，每个段落第一行以该段第一句的 [#序号] 开头（只写序号，不写时间），例如：
[#12] 整理后的段落文字……
只输出整理后的段落，不要任何解释。
"""

SCENE_GUIDES = {
    "MEETING": "这是一场会议：keyPoints 按议题组织，突出决议和分歧点；todos 写清分工（谁做什么）。",
    "LECTURE": "这是一堂课或讲座：keyPoints 突出知识点、定义和例子；chapters 体现课程结构；todos 写课后作业或复习要点。",
    "INTERVIEW": "这是一次访谈：keyPoints 按问答脉络组织，突出受访者的观点；quotes 优先选受访者原话。",
    "MEMO": "这是一段随手记录的想法：keyPoints 简短列出要点，chapters 可以为空数组。",
    "SYSTEM_AUDIO": "这是录制的电脑播放内容（网课、视频等）：按课程或视频内容整理，接近讲座，不要假定有多人讨论。",
}
SCENE_DETECT = (
    '先判断录音场景，在 JSON 里加一个 "scene" 字段，只能取 MEETING（会议）/ LECTURE（课堂、讲座）/ '
    "INTERVIEW（访谈）/ MEMO（随手想法或其他），再按对应场景的侧重点整理：\n"
    + "\n".join(f"- {k}：{SCENE_GUIDES[k]}" for k in ("MEETING", "LECTURE", "INTERVIEW", "MEMO"))
)
SPEAKER_HINT = "每行里的「说话人N」是语音识别区分出的说话人编号。总结里提到某位说话人时写成 {{SN}}（例如 {{S1}}），不要自己给说话人起名字。"


class AsrTranscribeRequest(BaseModel):
    noteId: int
    blockId: str
    segment: int | None = None
    offsetMs: int = 0


class AsrTranscribeResponse(BaseModel):
    utterances: list[dict]


class Utterance(BaseModel):
    start: int
    end: int
    text: str
    speaker: int | None = None


class AudioSummarizeRequest(BaseModel):
    utterances: list[Utterance]
    marks: list[int] = []
    scene: str | None = None
    systemAudio: bool = False


class AudioSummarizeResponse(BaseModel):
    polished: list[dict]
    summary: dict
    scene: str


def _error(status: int, code: str, message: str) -> HTTPException:
    return HTTPException(status_code=status, detail={"code": code, "message": message})


async def _load_settings():
    java_client = JavaClient()
    try:
        cfg = await load_effective_settings(java_client)
    finally:
        await java_client.aclose()
    use_settings(cfg)
    return cfg


@router.post("/internal/asr-transcribe")
async def asr_transcribe(request: AsrTranscribeRequest) -> AsrTranscribeResponse:
    cfg = await _load_settings()
    if not cfg.volcengine_asr_app_key:
        # 没配 Key 就别白白去取音频（可能几十 MB）
        raise _error(422, "ASR_NOT_CONFIGURED", "请到 设置 → AI 服务 填写语音识别 Key")
    java_client = JavaClient()
    try:
        audio = await java_client.get_block_audio(request.noteId, request.blockId, segment=request.segment)
    except JavaClientError as e:
        raise _error(502, "AUDIO_FETCH_FAILED", f"读取录音文件失败：{e}") from e
    finally:
        await java_client.aclose()
    if len(audio) > ASR_MAX_BYTES:
        raise _error(413, "AUDIO_TOO_LARGE", "这段音频超过 100MB，语音识别一次处理不了")

    asr_client = VolcengineAsrClient()
    try:
        utterances = await asr_client.transcribe_utterances(base64.b64encode(audio).decode())
    except AsrNotConfiguredError as e:
        raise _error(422, "ASR_NOT_CONFIGURED", str(e)) from e
    except AsrClientError as e:
        raise _error(502, "ASR_FAILED", str(e)) from e
    finally:
        await asr_client.aclose()
    shifted = [{**u, "start": u["start"] + request.offsetMs, "end": u["end"] + request.offsetMs}
               for u in utterances]
    return AsrTranscribeResponse(utterances=shifted)


def _summary_prompt(utterances: list[dict], windows: list[dict], scene: str | None) -> str:
    multi = has_multiple_speakers(utterances)
    prompt = SUMMARY_PROMPT + "\n" + (SCENE_GUIDES[scene] if scene else SCENE_DETECT) + "\n"
    if multi:
        prompt += SPEAKER_HINT + "\n"
    prompt += "\n" + format_indexed(utterances, with_speaker=multi)
    if windows:
        lines = [f"标记 {k}（{fmt_ts(w['markAt'])}）：句子 #{w['indices'][0]} 到 #{w['indices'][-1]}"
                 for k, w in enumerate(windows)]
        prompt += "\n\n用户在录音时打了以下标记，请在 highlights 里为每个标记各写一句话概括：\n" + "\n".join(lines)
    return prompt


async def _polish_chunk(model, utterances: list[dict], chunk: list[int], sem: asyncio.Semaphore) -> list[dict]:
    async with sem:
        try:
            response = await model.ainvoke([HumanMessage(POLISH_PROMPT + "\n" + format_indexed(utterances, chunk))])
            return parse_polished(extract_text_content(response.content), utterances, chunk)
        except Exception:
            # 某一块整理失败不连累其他块：这一块退回原文
            return fallback_polished(utterances, chunk)


@router.post("/internal/audio-summarize")
async def audio_summarize(request: AudioSummarizeRequest) -> AudioSummarizeResponse:
    cfg = await _load_settings()
    model = resolve_chat_model(cfg.default_provider)
    utterances = [u.model_dump() for u in request.utterances]
    windows = mark_windows(utterances, request.marks)
    sem = asyncio.Semaphore(POLISH_CONCURRENCY)
    scene = normalize_scene(request.scene) if request.scene else ("SYSTEM_AUDIO" if request.systemAudio else None)
    detected = {}

    async def summarize() -> dict:
        response = await model.ainvoke([HumanMessage(_summary_prompt(utterances, windows, scene))])
        raw = extract_json(extract_text_content(response.content))
        detected["scene"] = scene or normalize_scene(raw.get("scene"))
        return resolve_summary(raw, utterances, windows)

    polish_tasks = [_polish_chunk(model, utterances, chunk, sem) for chunk in chunk_indices(utterances)]
    results = await asyncio.gather(summarize(), *polish_tasks, return_exceptions=True)
    summary = results[0]
    if isinstance(summary, BaseException):
        raise _error(502, "SUMMARY_FAILED", f"整理失败：{summary}")
    polished = [p for part in results[1:] for p in part]
    return AudioSummarizeResponse(polished=polished, summary=summary, scene=detected["scene"])
