# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""把mindio的附件格式（{type, mimeType, base64Data}，和Java端ChatService.Attachment
一致）转换成LangChain的多模态消息内容块。

图片：用标准的OpenAI风格 image_url data URI 块——langchain_anthropic 内部会自动把
这种格式转换成Anthropic原生的source块，所以四个provider可以共用同一种构造方式，
不需要按provider分支。

文档：Anthropic原生API支持base64内联PDF（document块），直接用它自己的格式；但
OpenAI风格的Chat Completions接口对内联base64文档的支持因provider而异（多数要求
先走文件上传接口，而不是消息里塞base64），这里先按"尽力而为"实现，如果某个
provider实际不支持，需要在实施/联调阶段验证并记录清晰的降级提示（比如提示用户
"当前provider不支持文档附件"），不能静默丢弃附件。
"""
from __future__ import annotations

from langchain_core.messages import HumanMessage


def build_human_message(content: str, attachments: list[dict] | None, provider: str) -> HumanMessage:
    if not attachments:
        return HumanMessage(content=content or "")

    blocks: list[dict] = []
    if content:
        blocks.append({"type": "text", "text": content})

    for att in attachments:
        att_type = att.get("type")
        mime_type = att.get("mimeType")
        data = att.get("base64Data")
        data_url = f"data:{mime_type};base64,{data}"

        if att_type == "image":
            blocks.append({"type": "image_url", "image_url": {"url": data_url}})
        elif att_type == "document":
            if provider == "anthropic":
                blocks.append({
                    "type": "document",
                    "source": {"type": "base64", "media_type": mime_type, "data": data},
                })
            else:
                # OpenAI兼容接口对内联base64文档的支持不稳定，这里先按同样的data URI
                # 格式尝试传递；如果目标provider实际不支持，需要在联调阶段验证并改成
                # 明确的降级提示，而不是让请求静默失败或丢内容。
                blocks.append({"type": "image_url", "image_url": {"url": data_url}})

    return HumanMessage(content=blocks)
