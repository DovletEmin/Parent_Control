from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import ORJSONResponse

from app.api.router import api_router
from app.config import get_settings
from app.ws.device_ws import device_ws_handler
from app.ws.parent_ws import parent_ws_handler

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    from app.database import engine

    yield
    # Shutdown
    await engine.dispose()


app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
    docs_url="/docs" if settings.debug else None,
    redoc_url="/redoc" if settings.debug else None,
    default_response_class=ORJSONResponse,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"] if settings.debug else settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix=settings.api_v1_prefix)


# ── WebSocket endpoints ──────────────────────────────────────────────

@app.websocket("/ws/device/{device_token}")
async def ws_device(websocket: WebSocket, device_token: str):
    await device_ws_handler(websocket, device_token)


@app.websocket("/ws/parent")
async def ws_parent(websocket: WebSocket, access_token: str = ""):
    await parent_ws_handler(websocket, access_token)


# ── Health check ─────────────────────────────────────────────────────

@app.get("/health", tags=["health"])
async def health_check():
    return {"status": "ok", "app": settings.app_name}
