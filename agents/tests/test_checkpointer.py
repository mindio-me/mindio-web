# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

from langgraph.checkpoint.base import empty_checkpoint

from app.checkpointer import JavaBackedCheckpointer


class FakeJavaClient:
    """内存版JavaClient替身：真实实现是HTTP调Java，这里只验证get_state/put_state之间
    的数据能不能正确往返，不需要真的起Java服务。"""

    def __init__(self):
        self._store: dict[str, str] = {}

    async def get_state(self, conversation_id: str) -> str | None:
        return self._store.get(conversation_id)

    async def put_state(self, conversation_id: str, state_blob: str) -> None:
        self._store[conversation_id] = state_blob


def _config(thread_id: str) -> dict:
    return {"configurable": {"thread_id": thread_id, "checkpoint_ns": ""}}


async def test_hydrate_on_empty_state_is_a_noop():
    java = FakeJavaClient()
    checkpointer = JavaBackedCheckpointer(java, "alice")

    await checkpointer.hydrate()

    assert checkpointer.get_tuple(_config("alice")) is None


async def test_put_then_flush_then_reload_in_new_instance_roundtrips():
    java = FakeJavaClient()
    first = JavaBackedCheckpointer(java, "alice")
    await first.hydrate()

    checkpoint = empty_checkpoint()
    checkpoint["channel_values"] = {"messages": ["hello"]}
    checkpoint["channel_versions"] = {"messages": "1"}
    saved_config = first.put(_config("alice"), checkpoint, {"source": "input", "step": 1}, {"messages": "1"})
    await first.flush()

    second = JavaBackedCheckpointer(java, "alice")
    await second.hydrate()
    tuple_ = second.get_tuple(saved_config)

    assert tuple_ is not None
    assert tuple_.checkpoint["channel_values"] == {"messages": ["hello"]}


async def test_different_conversation_ids_do_not_see_each_others_state():
    java = FakeJavaClient()
    alice = JavaBackedCheckpointer(java, "alice")
    await alice.hydrate()
    checkpoint = empty_checkpoint()
    checkpoint["channel_values"] = {"messages": ["alice's message"]}
    checkpoint["channel_versions"] = {"messages": "1"}
    alice.put(_config("alice"), checkpoint, {"source": "input", "step": 1}, {"messages": "1"})
    await alice.flush()

    bob = JavaBackedCheckpointer(java, "bob")
    await bob.hydrate()

    assert bob.get_tuple(_config("bob")) is None
