import uuid
from datetime import date, datetime

from pydantic import BaseModel, Field


class AppUsageSyncItem(BaseModel):
    package_name: str = Field(..., max_length=255)
    app_name: str = Field(..., max_length=255)
    usage_seconds: int = Field(..., ge=0)
    date: date
    started_at: datetime | None = None
    ended_at: datetime | None = None


class AppUsageSyncRequest(BaseModel):
    items: list[AppUsageSyncItem] = Field(..., max_length=500)


class AppUsageResponse(BaseModel):
    id: uuid.UUID
    device_id: uuid.UUID
    package_name: str
    app_name: str
    usage_seconds: int
    date: date
    started_at: datetime | None
    ended_at: datetime | None
    created_at: datetime

    model_config = {"from_attributes": True}


class AppUsageStatsResponse(BaseModel):
    package_name: str
    app_name: str
    total_seconds: int
    date: date


class AppBlockRequest(BaseModel):
    package_name: str = Field(..., max_length=255)
    blocked: bool = True
