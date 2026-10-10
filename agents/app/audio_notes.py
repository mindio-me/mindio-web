"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only

录音整理用的纯函数：给逐句原文编号、切块、算标记窗口、解析模型输出并把"句子序号"换算回毫秒。
模型只引用序号、不自己算时间戳——时间戳一律由这里从 ASR 的逐句时间换算，避免模型编时间。
详见 docs/superpowers/specs/2026-10-10-recording-auto-notes-design.md。
"""
from __future__ import annotations

import json
import re

MARK_BEFORE_MS = 20_000
MARK_AFTER_MS = 10_000
POLISH_CHUNK_CHARS = 3000

_PARAGRAPH_HEAD = re.compile(r"^\s*\[#(\d+)\]\s*")


def fmt_ts(ms: int) -> str:
    s = max(0, int(ms) // 1000)
    h, rem = divmod(s, 3600)
    m, sec = divmod(rem, 60)
    return f"{h}:{m:02d}:{sec:02d}" if h else f"{m:02d}:{sec:02d}"


def format_indexed(utterances: list[dict], indices: list[int] | None = None) -> str:
    idx = range(len(utterances)) if indices is None else indices
    return "\n".join(f"[#{i} {fmt_ts(utterances[i]['start'])}] {utterances[i]['text']}" for i in idx)


def chunk_indices(utterances: list[dict], max_chars: int = POLISH_CHUNK_CHARS) -> list[list[int]]:
    chunks: list[list[int]] = []
    current: list[int] = []
    size = 0
    for i, u in enumerate(utterances):
        n = len(u["text"])
        if current and size + n > max_chars:
            chunks.append(current)
            current, size = [], 0
        current.append(i)
        size += n
    if current:
        chunks.append(current)
    return chunks


def mark_windows(utterances: list[dict], marks: list[int],
                 before_ms: int = MARK_BEFORE_MS, after_ms: int = MARK_AFTER_MS) -> list[dict]:
    windows = []
    for mark in sorted(int(m) for m in marks):
        lo, hi = mark - before_ms, mark + after_ms
        indices = [i for i, u in enumerate(utterances) if u["end"] >= lo and u["start"] <= hi]
        if indices:
            windows.append({"markAt": mark, "indices": indices})
    return windows


def extract_json(text: str) -> dict:
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("模型输出里没有 JSON 对象")
    try:
        value = json.loads(text[start:end + 1])
    except json.JSONDecodeError as e:
        raise ValueError(f"模型输出的 JSON 解析失败: {e}") from e
    if not isinstance(value, dict):
        raise ValueError("模型输出的 JSON 不是对象")
    return value


def parse_polished(text: str, utterances: list[dict], chunk: list[int]) -> list[dict]:
    allowed = set(chunk)
    paragraphs: list[dict] = []
    for line in text.splitlines():
        m = _PARAGRAPH_HEAD.match(line)
        if m and int(m.group(1)) in allowed:
            paragraphs.append({"start": utterances[int(m.group(1))]["start"], "text": line[m.end():].strip()})
            continue
        body = line[m.end():] if m else line
        if paragraphs and body.strip():
            paragraphs[-1]["text"] = (paragraphs[-1]["text"] + "\n" + body.strip()).strip()
    paragraphs = [p for p in paragraphs if p["text"]]
    if not paragraphs:
        raise ValueError("优化版里没有解析出带序号的段落")
    return paragraphs


def fallback_polished(utterances: list[dict], chunk: list[int]) -> list[dict]:
    return [{"start": utterances[chunk[0]]["start"], "text": "".join(utterances[i]["text"] for i in chunk)}]


def _index(value, size: int) -> int | None:
    try:
        i = int(value)
    except (TypeError, ValueError):
        return None
    return i if 0 <= i < size else None


def _texts(values) -> list[str]:
    return [str(v).strip() for v in (values or []) if str(v).strip()]


def resolve_summary(raw: dict, utterances: list[dict], windows: list[dict]) -> dict:
    n = len(utterances)
    key_points = []
    for kp in raw.get("keyPoints") or []:
        points = _texts(kp.get("points"))
        if points:
            key_points.append({"topic": str(kp.get("topic") or "").strip(), "points": points})
    chapters = []
    for c in raw.get("chapters") or []:
        i = _index(c.get("index"), n)
        if i is not None:
            chapters.append({"start": utterances[i]["start"], "title": str(c.get("title") or "").strip(),
                             "desc": str(c.get("desc") or "").strip()})
    quotes = []
    for q in raw.get("quotes") or []:
        i = _index(q.get("index"), n)
        text = str(q.get("text") or "").strip()
        if i is not None and text:
            quotes.append({"text": text, "category": str(q.get("category") or "").strip(),
                           "start": utterances[i]["start"]})
    highlights = []
    for h in raw.get("highlights") or []:
        k = _index(h.get("mark"), len(windows))
        text = str(h.get("text") or "").strip()
        if k is None or not text:
            continue
        idx = windows[k]["indices"]
        highlights.append({"markAt": windows[k]["markAt"], "start": utterances[idx[0]]["start"],
                           "end": utterances[idx[-1]]["end"], "text": text})
    return {
        "overview": str(raw.get("overview") or "").strip(),
        "keyPoints": key_points,
        "chapters": chapters,
        "quotes": quotes,
        "todos": _texts(raw.get("todos")),
        "highlights": highlights,
    }
