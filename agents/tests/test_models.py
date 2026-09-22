# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

from langchain_anthropic import ChatAnthropic
from langchain_openai import ChatOpenAI

from app.models import resolve_chat_model


def test_anthropic_provider_resolves_to_chat_anthropic(test_settings):
    model = resolve_chat_model("anthropic", test_settings)

    assert isinstance(model, ChatAnthropic)
    assert model.model == "m"
    assert model.anthropic_api_url == "https://api.anthropic.com"


def test_openai_compatible_providers_resolve_to_chat_openai(test_settings):
    for provider in ("openai", "deepseek", "doubao"):
        model = resolve_chat_model(provider, test_settings)

        assert isinstance(model, ChatOpenAI)
        assert model.model_name == "m"
        assert model.openai_api_base == test_settings.providers[provider].base_url
