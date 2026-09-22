# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""按provider名解析出绑好参数的LangChain ChatModel实例。deepseek/doubao走
ChatOpenAI的自定义base_url方式接入（这几家都提供OpenAI兼容接口），和Java端
现有OpenAiCompatibleChatService是同一套路，字段结构对齐 app/config.py 里的
ProviderConfig（本身又对齐Java的AiProperties.ProviderConfig）。

deepseek/doubao在"工具调用+流式输出"组合下的稳定性，需要写代码阶段用真实API手动
验证一次；如果某家表现不稳定，在这里记录降级方案（比如该provider下先不支持工具
调用）。目前尚未验证，先按标准OpenAI兼容协议实现。
"""
from __future__ import annotations

from langchain_anthropic import ChatAnthropic
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_openai import ChatOpenAI

from app.config import Settings, settings as default_settings

DEFAULT_MAX_TOKENS = 4096


def resolve_chat_model(provider: str, cfg: Settings = default_settings) -> BaseChatModel:
    provider_cfg = cfg.providers[provider]
    if provider == "anthropic":
        return ChatAnthropic(
            model=provider_cfg.model,
            anthropic_api_key=provider_cfg.api_key,
            anthropic_api_url=provider_cfg.base_url,
            max_tokens=DEFAULT_MAX_TOKENS,
            streaming=True,
        )
    # openai / deepseek / doubao 都是OpenAI兼容接口，只是base_url/model不同
    return ChatOpenAI(
        model_name=provider_cfg.model,
        openai_api_key=provider_cfg.api_key,
        openai_api_base=provider_cfg.base_url,
        max_tokens=DEFAULT_MAX_TOKENS,
        streaming=True,
    )
