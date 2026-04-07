import uuid

from fastapi import APIRouter

from app.core.dependencies import CurrentDevice, CurrentUser, DBSession
from app.schemas.device import (
    DeviceCreateRequest,
    DevicePairRequest,
    DevicePairResponse,
    DeviceResponse,
    DeviceUpdateFCMRequest,
    DeviceWithPairingCodeResponse,
)
from app.services.device_service import DeviceService

router = APIRouter(prefix="/devices", tags=["devices"])


@router.post("", response_model=DeviceWithPairingCodeResponse, status_code=201)
async def create_device(data: DeviceCreateRequest, user: CurrentUser, db: DBSession):
    """Create a new device slot and get a pairing code. (Parent endpoint)"""
    return await DeviceService.create_device(db, user, data)


@router.post("/pair", response_model=DevicePairResponse)
async def pair_device(data: DevicePairRequest, db: DBSession):
    """Pair a device using a pairing code. (Child device endpoint)"""
    return await DeviceService.pair_device(db, data)


@router.get("", response_model=list[DeviceResponse])
async def list_devices(user: CurrentUser, db: DBSession):
    """List all devices for the current parent."""
    return await DeviceService.get_user_devices(db, user)


@router.get("/{device_id}", response_model=DeviceResponse)
async def get_device(device_id: uuid.UUID, user: CurrentUser, db: DBSession):
    """Get a specific device by ID."""
    return await DeviceService.get_device(db, user, device_id)


@router.delete("/{device_id}", status_code=204)
async def delete_device(device_id: uuid.UUID, user: CurrentUser, db: DBSession):
    """Delete a device."""
    await DeviceService.delete_device(db, user, device_id)


@router.post("/fcm", status_code=204)
async def update_fcm_token(
    data: DeviceUpdateFCMRequest, device: CurrentDevice, db: DBSession
):
    """Update FCM token for the device. (Child device endpoint)"""
    await DeviceService.update_fcm_token(db, device, data)


@router.post("/heartbeat", status_code=204)
async def heartbeat(device: CurrentDevice, db: DBSession):
    """Device heartbeat to keep online status. (Child device endpoint)"""
    await DeviceService.heartbeat(db, device)
