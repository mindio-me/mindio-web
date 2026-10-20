# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

"""
运行配置：对齐 Java 端 spring-boot 的 AiProperties.ProviderConfig 结构，方便核对两边配置
是否一致。全部从环境变量读取（不做 .env 自动加载，本地开发时自己 export 或用
shell 的方式加载 .env），生产环境同样通过环境变量注入。
"""
import dataclasses
import os
from contextvars import ContextVar, Token
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
    # 分享卡片 AI 背景图：用哪家的图片模型（复用 providers 里同名配置的 Key/地址），空 = 未配置
    image_provider: str = ""
    image_model: str = ""


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
        default_provider=os.environ.get("DEFAULT_PROVIDER", "deepseek"),
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
        image_provider=os.environ.get("IMAGE_PROVIDER", ""),
        image_model=os.environ.get("IMAGE_MODEL", ""),
    )


settings = load_settings()


_current: ContextVar["Settings | None"] = ContextVar("mindio_effective_settings", default=None)


def merge_remote(base: Settings, remote: dict | None) -> Settings:
    """后端 /internal/ai-config 给的是界面里填过的值，逐字段覆盖环境变量；没给的保持环境变量。
    录音转写的两个 Key 是一对（新旧两种鉴权模式互斥），库里给了任意一个就整对替换。"""
    if not remote:
        return base
    providers = dict(base.providers)
    for name, override in (remote.get("providers") or {}).items():
        if name not in providers or not isinstance(override, dict):
            continue
        cur = providers[name]
        providers[name] = dataclasses.replace(
            cur,
            api_key=override.get("apiKey") or cur.api_key,
            model=override.get("model") or cur.model,
            base_url=override.get("baseUrl") or cur.base_url,
            chat_path=override.get("chatPath") or cur.chat_path,
        )
    asr = remote.get("asr") or {}
    if asr.get("appKey") or asr.get("accessKey"):
        asr_app_key, asr_access_key = asr.get("appKey") or "", asr.get("accessKey") or ""
    else:
        asr_app_key, asr_access_key = base.volcengine_asr_app_key, base.volcengine_asr_access_key
    image = remote.get("image") or {}
    return dataclasses.replace(
        base,
        image_provider=image.get("provider") or base.image_provider,
        image_model=image.get("model") or base.image_model,
        default_provider=remote.get("defaultProvider") or base.default_provider,
        providers=providers,
        volcengine_asr_app_key=asr_app_key,
        volcengine_asr_access_key=asr_access_key,
    )


def current_settings() -> Settings:
    """当前请求合并后的配置（路由入口用 use_settings 设置）；请求之外就是环境变量。"""
    return _current.get() or settings


def use_settings(cfg: Settings) -> Token:
    return _current.set(cfg)
