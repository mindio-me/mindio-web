# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""LangChain聊天模型返回的content可能是纯字符串，也可能是内容块列表（多模态响应
统一格式），这个helper把两种形态都拍平成纯文本。原本只有vision.py（OCR）用，
analyze_image工具（图片描述）用的是同一个模型调用链路，同样需要这个逻辑，抽出来
避免两份几乎一样的代码分叉。
"""
from __future__ import annotations


def extract_text_content(content) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(
            block.get("text", "") for block in content
            if isinstance(block, dict) and block.get("type") == "text"
        )
    return ""
