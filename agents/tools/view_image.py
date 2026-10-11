"""带着问题看用户任意一篇笔记/收藏里的图片，结果只返回给agent，不写回笔记。

和 analyze_image 分工：analyze_image 只能看当前笔记里 EditorJS 图片块，提示词固定为
"一两句话描述"，结果直接写进图片说明；这里是只读的"看图回答问题"，能看任意一篇自己的
笔记/收藏，富文本/Markdown 里内嵌的图片也能看（比如架构图、界面截图里的细节）。
"""
from __future__ import annotations

from typing import Annotated

from langchain_core.messages import HumanMessage
from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.config import current_settings
from app.java_client import JavaClient, JavaClientError
from app.message_content import extract_text_content
from app.models import resolve_chat_model
from app.state import AgentState

VIEW_PROMPT = (
    "下面是用户笔记/收藏里的一张图片。请结合图片内容回答问题；图里的文字、结构、"
    "连线关系、界面字段等细节如果和问题相关，要具体写出来，看不清或图里没有的不要编造。\n\n问题："
)


def make_view_image_tool(java_client: JavaClient):
    @tool
    async def view_image(
        question: str,
        state: Annotated[AgentState, InjectedState],
        note_id: int | None = None,
        clip_id: int | None = None,
        index: int | None = None,
    ) -> str:
        """查看用户某篇笔记或收藏里的一张图片，并就question回答（只读，不改动笔记）。
        用户提到"参考某篇里的截图/架构图"、或者read_note/read_clip提示文档里有图片、需要
        了解图里的细节时调用。note_id和clip_id都不传时看当前正在编辑的笔记。index是图片
        从上到下的序号（从1开始）；文档里有多张图又不确定看哪张时不传index，工具会列出
        所有图片及说明供你挑选，需要的话可以逐张查看。"""
        username = state["username"]
        if clip_id is not None:
            kind, doc_id, label = "clips", clip_id, "收藏"
        else:
            kind, doc_id, label = "notes", note_id or state.get("current_note_id"), "笔记"
            if not doc_id:
                return "需要提供 note_id 或 clip_id（当前也没有正在编辑的笔记）。"

        try:
            images = await java_client.list_document_images(username, kind, doc_id)
        except JavaClientError as e:
            return f"读取图片列表失败：{e}"
        if images is None:
            return f"找不到 id={doc_id} 的{label}（不存在或不属于当前用户）。"
        if not images:
            return f"这篇{label}里没有可查看的图片。"

        if index is None and len(images) == 1:
            index = 1
        if index is None or not 1 <= index <= len(images):
            listing = "\n".join(
                f"- 第{img['index']}张" + (f"：{img['caption']}" if img.get("caption") else "")
                for img in images
            )
            return f"这篇{label}里有{len(images)}张图片，请用 index 指定要看哪一张：\n{listing}"

        try:
            media = await java_client.get_document_image(username, kind, doc_id, index)
        except JavaClientError as e:
            return f"读取第{index}张图片失败：{e}"
        image_url = media.get("url") or f"data:{media['mimeType']};base64,{media['base64Data']}"

        model = resolve_chat_model(current_settings().default_provider)
        message = HumanMessage(content=[
            {"type": "text", "text": VIEW_PROMPT + question},
            {"type": "image_url", "image_url": {"url": image_url}},
        ])
        try:
            response = await model.ainvoke([message])
        except Exception as e:
            return f"看图失败：{e}（可能是当前默认模型不支持图片理解，或外链图片无法访问）"
        answer = extract_text_content(response.content).strip()
        if not answer:
            return "没能从这张图里得到有效回答。"
        return f"（{label} id={doc_id} 第{index}/{len(images)}张图）{answer}"

    return view_image
