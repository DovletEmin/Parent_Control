import uuid
from datetime import date

from fastapi import APIRouter, Query

from app.core.dependencies import CurrentDevice, CurrentUser, DBSession
from app.schemas.app_usage import AppUsageResponse, AppUsageStatsResponse, AppUsageSyncRequest
from app.schemas.common import SyncResponse
from app.services.device_service import DeviceService
from app.services.sync_service import AppUsageSyncService

router = APIRouter(tags=["app-usage"])


@router.post(
    "/devices/apps/sync",
    response_model=SyncResponse,
    summary="Sync app usage data from device",
)
async def sync_app_usage(
    data: AppUsageSyncRequest,
    device: CurrentDevice,
    db: DBSession,
):
    """Device endpoint: sync app usage records."""
    return await AppUsageSyncService.sync(db, device, data)


@router.get(
    "/devices/{device_id}/apps",
    response_model=list[AppUsageResponse],
    summary="Get app usage records",
)
async def get_app_usage(
    device_id: uuid.UUID,
    user: CurrentUser,
    db: DBSession,
    date_from: date | None = Query(None),
    date_to: date | None = Query(None),
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
):
    """Parent endpoint: get app usage records for a device."""
    await DeviceService.get_device(db, user, device_id)
    return await AppUsageSyncService.get_apps(db, device_id, date_from, date_to, page, page_size)


@router.get(
    "/devices/{device_id}/apps/usage",
    response_model=list[AppUsageStatsResponse],
    summary="Get aggregated app usage stats",
)
async def get_app_usage_stats(
    device_id: uuid.UUID,
    user: CurrentUser,
    db: DBSession,
    date_from: date | None = Query(None),
    date_to: date | None = Query(None),
):
    """Parent endpoint: get aggregated app usage statistics."""
    await DeviceService.get_device(db, user, device_id)
    return await AppUsageSyncService.get_usage_stats(db, device_id, date_from, date_to)
