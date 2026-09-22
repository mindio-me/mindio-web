# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""
运行配置：对齐 Java 端 spring-boot 的 AiProperties.ProviderConfig 结构，方便核对两边配置
是否一致。全部从环境变量读取（不做 .env 自动加载，本地开发时自己 export 或用
shell 的方式加载 .env），生产环境同样通过环境变量注入。
"""
import os
from dataclasses import dataclass


@dataclass(frozen=True)
class ProviderConfig:
    api_key: str
    model: str
    base_url: str
    chat_path: str = "/v1/chat/completions"


@dataclass(frozen=True)
class Settings:
    java_internal_base_url: str
    internal_shared_token: str
    default_provider: str
    providers: dict[str, ProviderConfig]
    volcengine_asr_app_key: str
    volcengine_asr_access_key: str


def _provider_config(prefix: str, default_model: str, default_base_url: str,
                      default_chat_path: str = "/v1/chat/completions") -> ProviderConfig:
    return ProviderConfig(
        api_key=os.environ.get(f"{prefix}_API_KEY", ""),
        model=os.environ.get(f"{prefix}_MODEL", default_model),
        base_url=os.environ.get(f"{prefix}_BASE_URL", default_base_url),
        chat_path=os.environ.get(f"{prefix}_CHAT_PATH", default_chat_path),
    )


def load_settings() -> Settings:
    return Settings(
        java_internal_base_url=os.environ.get("JAVA_INTERNAL_BASE_URL", "http://localhost:8080/api"),
        internal_shared_token=os.environ.get("INTERNAL_SHARED_TOKEN", "dev-only-internal-token-change-in-production"),
        default_provider=os.environ.get("DEFAULT_PROVIDER", "anthropic"),
        providers={
            "anthropic": _provider_config("ANTHROPIC", "claude-opus-4-6", "https://api.anthropic.com"),
            "openai": _provider_config("OPENAI", "gpt-4o", "https://api.openai.com"),
            "deepseek": _provider_config("DEEPSEEK", "deepseek-chat", "https://api.deepseek.com"),
            "doubao": _provider_config(
                "DOUBAO", "doubao-seed-2-0-pro-260215", "https://ark.cn-beijing.volces.com",
                default_chat_path="/api/v3/chat/completions",
            ),
        },
        volcengine_asr_app_key=os.environ.get("VOLCENGINE_ASR_APP_KEY", ""),
        volcengine_asr_access_key=os.environ.get("VOLCENGINE_ASR_ACCESS_KEY", ""),
    )


settings = load_settings()
