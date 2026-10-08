"""
每处理一次请求向 Java 后端取一次界面配置的 AI 设置（不缓存：本机/内网调用只要几毫秒，
远小于一次模型调用），与 agents 自己的环境变量合并。后端不可用时只用环境变量继续，
不让整次请求失败。
"""
import logging

from app.config import Settings, merge_remote, settings as env_settings

logger = logging.getLogger(__name__)


async def load_effective_settings(java_client, base: Settings = env_settings) -> Settings:
    try:
        remote = await java_client.get_ai_config()
    except Exception as e:  # noqa: BLE001 - 后端不可用/超时/非200 都退回环境变量
        logger.warning("无法从后端读取 AI 配置，改用环境变量: %s", e)
        return base
    return merge_remote(base, remote)
