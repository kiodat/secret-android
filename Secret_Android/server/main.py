import os
from typing import Any
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from dotenv import load_dotenv

load_dotenv()
app = FastAPI(title="Secret API Proxy", version="1.0.0")

# For production, replace "*" with your app's actual web origin if applicable.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)

HORMOUZ_URL = os.getenv("HORMOUZ_API_URL", "https://api.hormouz.net/v1/chat/completions")
HORMOUZ_API_KEY = os.getenv("HORMOUZ_API_KEY", "")
HORMOUZ_MODEL = os.getenv("HORMOUZ_MODEL", "default")

class ChatRequest(BaseModel):
    messages: list[dict[str, Any]] = Field(min_length=1, max_length=20)
    model: str | None = None

@app.get("/health")
async def health():
    return {"ok": True, "service": "Secret API Proxy"}

@app.post("/api/chat")
async def chat(body: ChatRequest):
    if not HORMOUZ_API_KEY or HORMOUZ_API_KEY == "replace_me":
        raise HTTPException(status_code=503, detail="API key is not configured on the server.")
    payload = {
        "model": body.model or HORMOUZ_MODEL,
        "messages": body.messages,
    }
    timeout = httpx.Timeout(90.0, connect=20.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
        try:
            response = await client.post(
                HORMOUZ_URL,
                headers={"Authorization": f"Bearer {HORMOUZ_API_KEY}", "Content-Type": "application/json"},
                json=payload,
            )
        except httpx.RequestError as exc:
            raise HTTPException(status_code=502, detail=f"Could not reach the AI provider: {exc.__class__.__name__}")
    if response.status_code >= 400:
        # Pass through provider's status and body to make debugging possible.
        raise HTTPException(status_code=response.status_code, detail=response.text[:2000])
    try:
        data = response.json()
    except ValueError:
        raise HTTPException(status_code=502, detail="AI provider returned invalid JSON.")
    try:
        reply = data["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError):
        raise HTTPException(status_code=502, detail="Unexpected response format from AI provider.")
    return {"reply": reply, "provider_response": data}
