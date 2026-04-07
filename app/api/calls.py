import uuid
from datetime import datetime

from fastapi import APIRouter, Query

from app.core.dependencies import CurrentDevice, CurrentUser, DBSession
from app.schemas.call_log import CallLogResponse, CallLogSyncRequest
from app.schemas.common import SyncResponse
from app.services.call_log_service import CallLogSyncService
from app.services.device_service import DeviceService

router = APIRouter(tags=["calls"])


@router.post(
    "/devices/calls/sync",
    response_model=SyncResponse,
    summary="Sync call logs from device",
)
async def sync_calls(
    data: CallLogSyncRequest,
    device: CurrentDevice,
    db: DBSession,
):
    """Device endpoint: sync call log records."""
    return await CallLogSyncService.sync(db, device, data)


@router.get(
    "/devices/{device_id}/calls",
    response_model=list[CallLogResponse],
    summary="Get call logs",
)
async def get_calls(
    device_id: uuid.UUID,
    user: CurrentUser,
    db: DBSession,
    call_type: str | None = Query(None),
    date_from: datetime | None = Query(None),
    date_to: datetime | None = Query(None),
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
):
    """Parent endpoint: get call logs for a device."""
    await DeviceService.get_device(db, user, device_id)
    return await CallLogSyncService.get_calls(
        db, device_id, call_type, date_from, date_to, page, page_size
    )
