import dataclasses

from app.config import current_settings, merge_remote, use_settings
from app.effective_settings import load_effective_settings
from app.java_client import JavaClientError


def test_remote_values_override_env_field_by_field(test_settings):
    merged = merge_remote(test_settings, {
        "defaultProvider": "doubao",
        "providers": {"doubao": {"apiKey": "db-key"}, "unknown": {"apiKey": "x"}},
    })
    assert merged.default_provider == "doubao"
    assert merged.providers["doubao"].api_key == "db-key"
    assert merged.providers["doubao"].model == test_settings.providers["doubao"].model
    assert merged.providers["anthropic"] == test_settings.providers["anthropic"]
    assert "unknown" not in merged.providers


def test_empty_remote_keeps_env(test_settings):
    assert merge_remote(test_settings, {}) == test_settings
    assert merge_remote(test_settings, None) == test_settings


def test_asr_pair_from_remote_replaces_env_pair_as_a_unit(test_settings):
    # 库里只填了新版"豆包语音"的单个 Key：不能和 .env 里旧版的 access key 拼在一起（两种鉴权混用会报 grant not found）
    merged = merge_remote(test_settings, {"asr": {"appKey": "new-console-key"}})
    assert merged.volcengine_asr_app_key == "new-console-key"
    assert merged.volcengine_asr_access_key == ""


async def test_load_effective_settings_falls_back_to_env_when_backend_fails(test_settings):
    class Broken:
        async def get_ai_config(self):
            raise JavaClientError("connection refused")

    assert await load_effective_settings(Broken(), base=test_settings) == test_settings


async def test_load_effective_settings_merges_backend_values(test_settings):
    class Ok:
        async def get_ai_config(self):
            return {"defaultProvider": "openai"}

    cfg = await load_effective_settings(Ok(), base=test_settings)
    assert cfg.default_provider == "openai"


def test_use_settings_is_visible_through_current_settings(test_settings):
    custom = dataclasses.replace(test_settings, default_provider="deepseek")
    token = use_settings(custom)
    try:
        assert current_settings().default_provider == "deepseek"
    finally:
        token.var.reset(token)


def test_default_provider_is_deepseek_when_env_unset(monkeypatch):
    # 与 Java 端 application.yml 的 ai.provider 默认值（AI_PROVIDER:deepseek）保持一致
    from app.config import load_settings

    monkeypatch.delenv("DEFAULT_PROVIDER", raising=False)
    assert load_settings().default_provider == "deepseek"


def test_merge_remote_image_config(test_settings):
    merged = merge_remote(test_settings, {"image": {"provider": "doubao", "model": "m1"}})
    assert merged.image_provider == "doubao"
    assert merged.image_model == "m1"
    base = dataclasses.replace(test_settings, image_provider="openai", image_model="x")
    kept = merge_remote(base, {"defaultProvider": "doubao"})
    assert (kept.image_provider, kept.image_model) == ("openai", "x")
