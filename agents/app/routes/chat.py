# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""对Java暴露的流式聊天接口。事件格式尽量贴近Java现有的ChatStreamEvent schema
（type取值text_delta/tool_call/done/error），减少Java那边的转换逻辑。

current_note_context 由Java预先解析好传过来（Java已经有 loadOwnedNoteOrNull +
ContentChunkingService 那套逻辑），Python不需要另外起一个"查笔记"的内部接口，
延续"Java是唯一数据源"的原则。
"""
from __future__ import annotations

import json
import logging
from collections.abc import AsyncIterator

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, ToolMessage
from langgraph.types import Command
from pydantic import BaseModel

from app.checkpointer import JavaBackedCheckpointer
from app.config import settings
from app.graph import RECURSION_LIMIT, build_default_graph_builder
from app.java_client import JavaClient
from app.multimodal import build_human_message

router = APIRouter()
logger = logging.getLogger(__name__)


class AttachmentPayload(BaseModel):
    type: str
    mimeType: str
    base64Data: str


class ChatStreamRequest(BaseModel):
    username: str
    content: str
    conversationId: str
    currentNoteContext: str | None = None
    currentNoteId: int | None = None
    attachments: list[AttachmentPayload] | None = None


class ChatResumeRequest(BaseModel):
    conversationId: str
    proposalId: str
    decision: str


def _event(type_: str, **fields) -> str:
    return json.dumps({"type": type_, **fields}, ensure_ascii=False) + "\n"


def _dedupe_citations(citations: list[dict]) -> list[dict]:
    """去重逻辑：NOTE/CLIP按sourceId去重、只保留sourceType/sourceId（人类可读的标题
    需要Java按ID反查，Python这边不持有这份数据）；WEB按sourceUrl去重，title/sourceUrl
    原样保留——网络搜索结果本来就自带标题，Java不需要也没办法反查。"""
    seen: dict[tuple, dict] = {}
    for c in citations:
        source_type = c.get("sourceType")
        if source_type == "WEB":
            key = (source_type, c.get("sourceUrl"))
            seen.setdefault(key, {
                "sourceType": source_type,
                "sourceUrl": c.get("sourceUrl"),
                "title": c.get("title"),
            })
        else:
            key = (source_type, c.get("sourceId"))
            seen.setdefault(key, {"sourceType": source_type, "sourceId": c.get("sourceId")})
    return list(seen.values())


async def _drive_graph(
    graph, graph_input, config: dict, citations: list[dict], block_updates: list[dict],
    media_block_updates: list[dict],
) -> AsyncIterator[str]:
    """驱动一次图执行，把astream_events的细粒度事件转成text_delta/tool_call SSE事件。
    循环结束后检查是否有待确认的interrupt——interrupt()触发时GraphInterrupt不会从
    astream_events里抛出来（已用脚本验证过），必须等这轮事件流跑完后主动查
    graph.get_state(config).tasks[].interrupts，这是这套events API下唯一可靠的
    检测方式。有待确认项时不产出'done'事件，那一轮在'confirm_request'处结束，
    真正的最终回复要等resume那一轮（Task 5）才会来。
    """
    final_text = ""
    async for event in graph.astream_events(graph_input, config=config, version="v2"):
        kind = event["event"]
        if kind == "on_chat_model_stream":
            delta = event["data"]["chunk"].content
            if isinstance(delta, str) and delta:
                yield _event("text_delta", text=delta)
        elif kind == "on_chat_model_end":
            # 每一轮模型调用都会触发一次；工具调用轮的content是空字符串，最后一轮
            # （不再触发工具调用）才是真正的最终回复——用它做"权威"的最终文本，
            # 不用自己拼接text_delta（更稳妥：即使某个provider不支持逐token流式，
            # 这里依然能拿到完整正确的最终答案，不会因为拼不出delta而丢内容）。
            output_content = event["data"]["output"].content
            if isinstance(output_content, str) and output_content:
                final_text = output_content
        elif kind == "on_tool_start" and event.get("name") in ("search_workspace", "search_web"):
            query = event["data"].get("input", {}).get("query", "")
            yield _event("tool_call", query=query)

    state = graph.get_state(config)
    pending = [i for task in state.tasks for i in task.interrupts]
    if pending:
        interrupt_obj = pending[0]
        yield _event("confirm_request", proposalId=interrupt_obj.id, **interrupt_obj.value)
        return

    for update in block_updates:
        yield _event("block_updated", **update)

    for update in media_block_updates:
        yield _event("media_block_updated", **update)

    yield _event("done", content=final_text, citations=_dedupe_citations(citations))


async def _run_agent(request: ChatStreamRequest) -> AsyncIterator[str]:
    java_client = JavaClient()
    checkpointer = JavaBackedCheckpointer(java_client, request.conversationId)
    citations: list[dict] = []
    block_updates: list[dict] = []
    media_block_updates: list[dict] = []
    try:
        await checkpointer.hydrate()
        graph_builder, _tools = build_default_graph_builder(
            java_client, settings.default_provider,
            citations_sink=citations, block_update_sink=block_updates,
            media_block_update_sink=media_block_updates,
        )
        graph = graph_builder.compile(checkpointer=checkpointer)
        config = {
            "configurable": {"thread_id": request.conversationId},
            "recursion_limit": RECURSION_LIMIT,
        }

        # 上一轮如果留下了没配对ToolMessage的tool_calls，不清理就把新消息接上去发给
        # 模型，任何provider都会因为这个不合法的消息序列直接报错——而且这条会话会
        # 永久卡死（conversationId就是username，是唯一连续会话；不清理的话每次重试
        # 都在这条坏历史上再失败一次）。这种情况有两种来源，分别处理：
        #
        # 1) 确认卡片被用户晾在那没点（刷新页面/关浏览器/切走了）——图正停在
        #    interrupt那个点，走LangGraph官方的Command(resume=...)复原路径，把它
        #    当"拒绝"自动结清。
        # 2) 工具执行中途真的崩溃了、或者某些provider即使传了parallel_tool_calls=False
        #    仍然一次发出多个tool_calls而只有一个真正跑完（这两种情况实测都发生过，
        #    graph.get_state()此时没有pending interrupt，只是state.next没走完）——
        #    这种没法用resume恢复（没有暂停的协程等着恢复），直接给每个没配对结果的
        #    tool_call补一条占位ToolMessage，用update_state()把历史改合法。这个修复
        #    已经拿真实卡住的会话验证过确实有效。
        #
        # 不管走哪条路径，都不把清理过程的输出转发给用户，用户只看到自己这条新消息
        # 的正常回复。循环上限只是防御性的：正常情况一轮就能清完。
        state = graph.get_state(config)
        cleanup_rounds = 0
        while state.next and cleanup_rounds < 3:
            if any(t.interrupts for t in state.tasks):
                async for _ in _drive_graph(graph, Command(resume="reject"), config, [], [], []):
                    pass
            else:
                messages = state.values.get("messages", [])
                resolved_ids = {m.tool_call_id for m in messages if isinstance(m, ToolMessage)}
                orphaned = [
                    tc for m in messages if isinstance(m, AIMessage) and m.tool_calls
                    for tc in m.tool_calls if tc["id"] not in resolved_ids
                ]
                if not orphaned:
                    break  # state.next卡着但不是上面两种已知情况，别硬清，避免误伤
                graph.update_state(config, {"messages": [
                    ToolMessage(content="（上一次这次工具调用没有正常完成，已跳过）", tool_call_id=tc["id"])
                    for tc in orphaned
                ]})
            state = graph.get_state(config)
            cleanup_rounds += 1

        attachments = [a.model_dump() for a in request.attachments] if request.attachments else None
        human_message = build_human_message(request.content, attachments, settings.default_provider)

        input_state = {
            "messages": [human_message],
            "todos": [],
            "conversation_id": request.conversationId,
            "username": request.username,
            "current_note_context": request.currentNoteContext,
            "current_note_id": request.currentNoteId,
        }

        async for chunk in _drive_graph(graph, input_state, config, citations, block_updates, media_block_updates):
            yield chunk
    except Exception as e:  # noqa: BLE001 - 任何失败都要转成error事件让流正常结束，不是HTTP层500
        yield _event("error", content=f"抱歉，这次没能回复，换个说法试试？（{e}）")
    finally:
        try:
            await checkpointer.flush()
        except Exception:
            # 之前这里完全静默吞掉——真出过一次flush失败（state_blob超出MySQL
            # TEXT列的65535字节上限）没有任何日志可查，这条会话的checkpoint就那么
            # 冻结在坏状态里，排查花了很久才追到根上。至少要留一条日志能看见。
            logger.exception("flush checkpointer state failed for conversation_id=%r", request.conversationId)
        await java_client.aclose()


async def _resume_agent(request: ChatResumeRequest) -> AsyncIterator[str]:
    java_client = JavaClient()
    checkpointer = JavaBackedCheckpointer(java_client, request.conversationId)
    citations: list[dict] = []
    block_updates: list[dict] = []
    media_block_updates: list[dict] = []
    try:
        await checkpointer.hydrate()
        graph_builder, _tools = build_default_graph_builder(
            java_client, settings.default_provider,
            citations_sink=citations, block_update_sink=block_updates,
            media_block_update_sink=media_block_updates,
        )
        graph = graph_builder.compile(checkpointer=checkpointer)
        config = {
            "configurable": {"thread_id": request.conversationId},
            "recursion_limit": RECURSION_LIMIT,
        }

        state = graph.get_state(config)
        pending = [i for task in state.tasks for i in task.interrupts]
        if not any(i.id == request.proposalId for i in pending):
            yield _event("error", content="这个提议已经不是待确认状态了，可能已经处理过或已过期。")
            return

        async for chunk in _drive_graph(
            graph, Command(resume=request.decision), config, citations, block_updates, media_block_updates
        ):
            yield chunk
    except Exception as e:  # noqa: BLE001
        yield _event("error", content=f"抱歉，这次没能回复，换个说法试试？（{e}）")
    finally:
        try:
            await checkpointer.flush()
        except Exception:
            # 之前这里完全静默吞掉——真出过一次flush失败（state_blob超出MySQL
            # TEXT列的65535字节上限）没有任何日志可查，这条会话的checkpoint就那么
            # 冻结在坏状态里，排查花了很久才追到根上。至少要留一条日志能看见。
            logger.exception("flush checkpointer state failed for conversation_id=%r", request.conversationId)
        await java_client.aclose()


@router.post("/internal/chat/stream")
async def stream_chat(request: ChatStreamRequest) -> StreamingResponse:
    return StreamingResponse(_run_agent(request), media_type="application/x-ndjson")


@router.post("/internal/chat/resume")
async def resume_chat(request: ChatResumeRequest) -> StreamingResponse:
    return StreamingResponse(_resume_agent(request), media_type="application/x-ndjson")
