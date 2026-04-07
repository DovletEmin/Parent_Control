from fastapi import APIRouter

from app.api.auth import router as auth_router
from app.api.devices import router as devices_router
from app.api.app_usage import router as app_usage_router
from app.api.calls import router as calls_router
from app.api.messages import router as messages_router
from app.api.media import router as media_router
from app.api.location import router as location_router
from app.api.commands import router as commands_router

api_router = APIRouter()

api_router.include_router(auth_router)
api_router.include_router(devices_router)
api_router.include_router(app_usage_router)
api_router.include_router(calls_router)
api_router.include_router(messages_router)
api_router.include_router(media_router)
api_router.include_router(location_router)
api_router.include_router(commands_router)
