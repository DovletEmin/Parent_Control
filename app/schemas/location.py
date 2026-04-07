import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class LocationSyncItem(BaseModel):
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    accuracy: float | None = Field(None, ge=0)
    altitude: float | None = None
    speed: float | None = Field(None, ge=0)
    recorded_at: datetime


class LocationSyncRequest(BaseModel):
    items: list[LocationSyncItem] = Field(..., max_length=1000)


class LocationResponse(BaseModel):
    id: uuid.UUID
    device_id: uuid.UUID
    latitude: float
    longitude: float
    accuracy: float | None
    altitude: float | None
    speed: float | None
    recorded_at: datetime
    created_at: datetime

    model_config = {"from_attributes": True}


class LocationLatestResponse(BaseModel):
    latitude: float
    longitude: float
    accuracy: float | None
    altitude: float | None
    speed: float | None
    recorded_at: datetime
