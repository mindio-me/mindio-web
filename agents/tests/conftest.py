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


@pytest.fixture(autouse=True)
def _no_backend_ai_config(monkeypatch, test_settings):
    import app.routes.chat as chat_route
    import app.routes.vision as vision_route

    async def fake_load(java_client, base=None):
        return test_settings

    monkeypatch.setattr(chat_route, "load_effective_settings", fake_load)
    monkeypatch.setattr(vision_route, "load_effective_settings", fake_load)
    import app.routes.audio as audio_route
    monkeypatch.setattr(audio_route, "load_effective_settings", fake_load)
    import app.routes.insert_position as insert_position_route
    monkeypatch.setattr(insert_position_route, "load_effective_settings", fake_load)
