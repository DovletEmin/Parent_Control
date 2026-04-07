import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class DeviceCreateRequest(BaseModel):
    name: str = Field(..., min_length=1, max_length=100)


class DevicePairRequest(BaseModel):
    pairing_code: str = Field(..., min_length=6, max_length=6)
    device_model: str | None = None
    android_version: str | None = None
    fcm_token: str | None = None


class DeviceUpdateFCMRequest(BaseModel):
    fcm_token: str


class DeviceResponse(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    name: str
    device_model: str | None
    android_version: str | None
    is_online: bool
    is_paired: bool
    last_seen_at: datetime | None
    created_at: datetime

    model_config = {"from_attributes": True}


class DeviceWithPairingCodeResponse(DeviceResponse):
    pairing_code: str | None


class DevicePairResponse(BaseModel):
    device_id: uuid.UUID
    device_token: str
    message: str = "Device paired successfully"
