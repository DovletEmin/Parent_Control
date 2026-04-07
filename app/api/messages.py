import uuid
from datetime import datetime

from fastapi import APIRouter, Query

from app.core.dependencies import CurrentDevice, CurrentUser, DBSession
from app.schemas.common import SyncResponse
from app.schemas.message import MessageResponse, MessageSyncRequest
from app.services.device_service import DeviceService
from app.services.message_service import MessageSyncService

router = APIRouter(tags=["messages"])


@router.post(
    "/devices/messages/sync",
    response_model=SyncResponse,
    summary="Sync messages from device",
)
async def sync_messages(
    data: MessageSyncRequest,
    device: CurrentDevice,
    db: DBSession,
):
    """Device endpoint: sync message records."""
    return await MessageSyncService.sync(db, device, data)


@router.get(
    "/devices/{device_id}/messages",
    response_model=list[MessageResponse],
    summary="Get messages",
)
async def get_messages(
    device_id: uuid.UUID,
    user: CurrentUser,
    db: DBSession,
    message_type: str | None = Query(None),
    contact: str | None = Query(None),
    date_from: datetime | None = Query(None),
    date_to: datetime | None = Query(None),
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
):
    """Parent endpoint: get messages for a device."""
    await DeviceService.get_device(db, user, device_id)
    return await MessageSyncService.get_messages(
        db, device_id, message_type, contact, date_from, date_to, page, page_size
    )
