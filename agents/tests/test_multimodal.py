# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

from app.multimodal import build_human_message


def test_no_attachments_returns_plain_text_content():
    msg = build_human_message("hello", None, "anthropic")

    assert msg.content == "hello"


def test_image_attachment_uses_image_url_block_for_any_provider():
    attachments = [{"type": "image", "mimeType": "image/png", "base64Data": "AAAA"}]

    for provider in ("anthropic", "openai", "deepseek", "doubao"):
        msg = build_human_message("look at this", attachments, provider)

        assert {"type": "text", "text": "look at this"} in msg.content
        image_blocks = [b for b in msg.content if b["type"] == "image_url"]
        assert len(image_blocks) == 1
        assert image_blocks[0]["image_url"]["url"] == "data:image/png;base64,AAAA"


def test_document_attachment_uses_anthropic_native_block_for_anthropic():
    attachments = [{"type": "document", "mimeType": "application/pdf", "base64Data": "BBBB"}]

    msg = build_human_message("read this", attachments, "anthropic")

    doc_blocks = [b for b in msg.content if b["type"] == "document"]
    assert len(doc_blocks) == 1
    assert doc_blocks[0]["source"] == {"type": "base64", "media_type": "application/pdf", "data": "BBBB"}


def test_document_attachment_falls_back_for_non_anthropic_providers():
    attachments = [{"type": "document", "mimeType": "application/pdf", "base64Data": "BBBB"}]

    msg = build_human_message("read this", attachments, "openai")

    assert not any(b["type"] == "document" for b in msg.content)
    assert any(b["type"] == "image_url" for b in msg.content)


def test_no_text_content_with_only_attachment():
    attachments = [{"type": "image", "mimeType": "image/png", "base64Data": "AAAA"}]

    msg = build_human_message("", attachments, "anthropic")

    assert not any(b.get("type") == "text" for b in msg.content)
