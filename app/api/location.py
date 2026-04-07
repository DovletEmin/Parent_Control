import uuid
from datetime import datetime

from fastapi import APIRouter, HTTPException, Query, status

from app.core.dependencies import CurrentDevice, CurrentUser, DBSession
from app.schemas.common import SyncResponse
from app.schemas.location import LocationLatestResponse, LocationResponse, LocationSyncRequest
from app.services.device_service import DeviceService
from app.services.location_service import LocationSyncService

router = APIRouter(tags=["location"])


@router.post(
    "/devices/location/sync",
    response_model=SyncResponse,
    summary="Sync location data from device",
)
async def sync_location(
    data: LocationSyncRequest,
    device: CurrentDevice,
    db: DBSession,
):
    """Device endpoint: sync location records."""
    return await LocationSyncService.sync(db, device, data)


@router.get(
    "/devices/{device_id}/location",
    response_model=list[LocationResponse],
    summary="Get location history",
)
async def get_locations(
    device_id: uuid.UUID,
    user: CurrentUser,
    db: DBSession,
    date_from: datetime | None = Query(None),
    date_to: datetime | None = Query(None),
    page: int = Query(1, ge=1),
    page_size: int = Query(100, ge=1, le=500),
):
    """Parent endpoint: get location history for a device."""
    await DeviceService.get_device(db, user, device_id)
    return await LocationSyncService.get_locations(
        db, device_id, date_from, date_to, page, page_size
    )


@router.get(
    "/devices/{device_id}/location/latest",
    response_model=LocationLatestResponse,
    summary="Get latest location",
)
async def get_latest_location(
    device_id: uuid.UUID,
    user: CurrentUser,
    db: DBSession,
):
    """Parent endpoint: get the latest known location."""
    await DeviceService.get_device(db, user, device_id)
    loc = await LocationSyncService.get_latest(db, device_id)
    if loc is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No location data available",
        )
    return loc
