# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

import pytest

from app.config import ProviderConfig, Settings


@pytest.fixture
def test_settings() -> Settings:
    return Settings(
        java_internal_base_url="http://java.internal.test",
        internal_shared_token="test-token",
        default_provider="anthropic",
        providers={
            "anthropic": ProviderConfig(api_key="k", model="m", base_url="https://api.anthropic.com"),
            "openai": ProviderConfig(api_key="k", model="m", base_url="https://api.openai.com"),
            "deepseek": ProviderConfig(api_key="k", model="m", base_url="https://api.deepseek.com"),
            "doubao": ProviderConfig(api_key="k", model="m", base_url="https://ark.cn-beijing.volces.com",
                                      chat_path="/api/v3/chat/completions"),
        },
        volcengine_asr_app_key="asr-app-key",
        volcengine_asr_access_key="asr-access-key",
    )
