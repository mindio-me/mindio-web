"""
Copyright (c) 2026 Fasong Wu
SPDX-License-Identifier: AGPL-3.0-only
"""
import pytest

from app.audio_notes import (
    SCENES, chunk_indices, extract_json, fallback_polished, fmt_ts, format_indexed,
    has_multiple_speakers, mark_windows, normalize_scene, parse_polished, resolve_summary,
)

U = [
    {"start": 0, "end": 4000, "text": "大家好。"},
    {"start": 5000, "end": 9000, "text": "今天讲三件事。"},
    {"start": 30000, "end": 34000, "text": "第一件是招聘。"},
    {"start": 3725000, "end": 3729000, "text": "最后总结。"},
]


def test_fmt_ts():
    assert fmt_ts(0) == "00:00"
    assert fmt_ts(65000) == "01:05"
    assert fmt_ts(3725000) == "1:02:05"


def test_format_indexed_uses_global_indices():
    assert format_indexed(U, [2, 3]) == "[#2 00:30] 第一件是招聘。\n[#3 1:02:05] 最后总结。"
    assert format_indexed(U).startswith("[#0 00:00] 大家好。\n[#1 00:05]")


def test_chunk_indices_splits_on_sentence_boundaries():
    utts = [{"start": i, "end": i, "text": "字" * 1000} for i in range(5)]
    assert chunk_indices(utts, max_chars=2500) == [[0, 1], [2, 3], [4]]


def test_chunk_indices_long_single_sentence_is_own_chunk():
    utts = [{"start": 0, "end": 0, "text": "字" * 5000}, {"start": 1, "end": 1, "text": "短"}]
    assert chunk_indices(utts, max_chars=3000) == [[0], [1]]


def test_mark_windows_selects_overlapping_sentences_and_drops_empty():
    windows = mark_windows(U, [3000000, 31000, 8000])
    assert windows == [
        {"markAt": 8000, "indices": [0, 1]},
        {"markAt": 31000, "indices": [2]},
    ]


def test_extract_json_strips_fences():
    assert extract_json('说明\n```json\n{"a": 1}\n```') == {"a": 1}
    with pytest.raises(ValueError):
        extract_json("没有json")


def test_parse_polished_maps_paragraph_start():
    text = "[#1] 今天讲三件事，\n第一件是招聘。\n[#3] 最后总结。"
    assert parse_polished(text, U, [1, 2, 3]) == [
        {"start": 5000, "text": "今天讲三件事，\n第一件是招聘。"},
        {"start": 3725000, "text": "最后总结。"},
    ]


def test_parse_polished_merges_paragraph_with_foreign_index_into_previous():
    text = "[#1] 甲。\n[#0] 乙。"
    assert parse_polished(text, U, [1, 2]) == [{"start": 5000, "text": "甲。\n乙。"}]


def test_parse_polished_raises_when_nothing_parsed():
    with pytest.raises(ValueError):
        parse_polished("没有序号的一段话", U, [0, 1])


def test_fallback_polished_joins_chunk():
    assert fallback_polished(U, [0, 1]) == [{"start": 0, "text": "大家好。今天讲三件事。"}]


def test_resolve_summary_maps_indices_to_ms():
    raw = {
        "overview": " 一次分享 ",
        "keyPoints": [{"topic": "方法", "points": ["**先定场景**：说明", ""]}],
        "chapters": [{"index": 2, "title": "招聘", "desc": "讲招聘"}],
        "quotes": [{"text": "原话", "category": "方法技巧", "index": 1}],
        "todos": ["发纪要", ""],
        "highlights": [{"mark": 0, "text": "重点是三件事"}],
    }
    windows = [{"markAt": 8000, "indices": [0, 1]}]
    assert resolve_summary(raw, U, windows) == {
        "overview": "一次分享",
        "keyPoints": [{"topic": "方法", "points": ["**先定场景**：说明"]}],
        "chapters": [{"start": 30000, "title": "招聘", "desc": "讲招聘"}],
        "quotes": [{"text": "原话", "category": "方法技巧", "start": 5000}],
        "todos": ["发纪要"],
        "highlights": [{"markAt": 8000, "start": 0, "end": 9000, "text": "重点是三件事"}],
    }


def test_resolve_summary_drops_out_of_range_indices():
    raw = {
        "overview": "x",
        "chapters": [{"index": 99, "title": "越界"}, {"index": "abc", "title": "坏"}, {"index": 0, "title": "好"}],
        "quotes": [{"text": "q", "index": -1}],
        "highlights": [{"mark": 5, "text": "越界"}],
    }
    result = resolve_summary(raw, U, [])
    assert result["chapters"] == [{"start": 0, "title": "好", "desc": ""}]
    assert result["quotes"] == []
    assert result["highlights"] == []
    assert result["keyPoints"] == [] and result["todos"] == []


def test_normalize_scene_accepts_known_values_case_insensitively():
    assert normalize_scene("meeting") == "MEETING"
    assert normalize_scene(" SYSTEM_AUDIO ") == "SYSTEM_AUDIO"
    assert set(SCENES) == {"MEETING", "LECTURE", "INTERVIEW", "MEMO", "SYSTEM_AUDIO"}


@pytest.mark.parametrize("value", [None, "", "PARTY", 3])
def test_normalize_scene_falls_back_to_memo(value):
    assert normalize_scene(value) == "MEMO"


def test_has_multiple_speakers():
    assert not has_multiple_speakers([{"text": "a"}, {"text": "b", "speaker": 1}])
    assert not has_multiple_speakers([{"text": "a", "speaker": 1}, {"text": "b", "speaker": 1}])
    assert has_multiple_speakers([{"text": "a", "speaker": 1}, {"text": "b", "speaker": 2}])


def test_format_indexed_adds_speaker_only_when_requested():
    utts = [{"start": 0, "end": 1, "text": "甲", "speaker": 1}, {"start": 61000, "end": 1, "text": "乙", "speaker": None}]
    assert format_indexed(utts) == "[#0 00:00] 甲\n[#1 01:01] 乙"
    assert format_indexed(utts, with_speaker=True) == "[#0 00:00 说话人1] 甲\n[#1 01:01] 乙"
