# Copyright (c) 2026 Fasong Wu
# SPDX-License-Identifier: AGPL-3.0-only

from fastapi import FastAPI

from app.routes.audio import router as audio_router
from app.routes.chat import router as chat_router
from app.routes.insert_position import router as insert_position_router
from app.routes.vision import router as vision_router

app = FastAPI(title="mindio-agents")
app.include_router(chat_router)
app.include_router(audio_router)
app.include_router(vision_router)
app.include_router(insert_position_router)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}
