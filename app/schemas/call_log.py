import uuid
from datetime import datetime

from pydantic import BaseModel, Field

from app.models.call_log import CallType


class CallLogSyncItem(BaseModel):
    phone_number: str = Field(..., max_length=50)
    contact_name: str | None = Field(None, max_length=255)
    call_type: CallType
    duration_seconds: int = Field(..., ge=0)
    called_at: datetime


class CallLogSyncRequest(BaseModel):
    items: list[CallLogSyncItem] = Field(..., max_length=500)


class CallLogResponse(BaseModel):
    id: uuid.UUID
    device_id: uuid.UUID
    phone_number: str
    contact_name: str | None
    call_type: str
    duration_seconds: int
    called_at: datetime
    created_at: datetime

    model_config = {"from_attributes": True}
