import logging
import os

import edge_tts
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

logger = logging.getLogger("edge-tts-service")

DEFAULT_VOICE = os.getenv("EDGE_TTS_DEFAULT_VOICE", "en-US-AriaNeural")
MAX_TEXT_CHARS = int(os.getenv("EDGE_TTS_MAX_TEXT_CHARS", "500"))

app = FastAPI(title="khan-kiddo edge-tts", version="1.0.0")


class TtsRequest(BaseModel):
    text: str = Field(min_length=1)
    voice: str | None = None


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/v1/tts")
async def tts(req: TtsRequest) -> Response:
    text = req.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="text is blank")
    if len(text) > MAX_TEXT_CHARS:
        raise HTTPException(status_code=400, detail=f"text exceeds {MAX_TEXT_CHARS} chars")
    voice = (req.voice or "").strip() or DEFAULT_VOICE

    audio = bytearray()
    try:
        communicate = edge_tts.Communicate(text, voice)
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                audio.extend(chunk["data"])
    except Exception as exc:
        logger.warning("edge-tts synthesis failed: %s", exc)
        raise HTTPException(status_code=502, detail="synthesis failed") from exc

    if not audio:
        raise HTTPException(status_code=502, detail="empty audio")
    return Response(content=bytes(audio), media_type="audio/mpeg")
