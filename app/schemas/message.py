import uuid
from datetime import datetime

from pydantic import BaseModel, Field

from app.models.message import MessageType


class MessageSyncItem(BaseModel):
    sender: str = Field(..., max_length=255)
    receiver: str | None = Field(None, max_length=255)
    body: str
    message_type: MessageType = MessageType.SMS
    is_incoming: bool = True
    sent_at: datetime


class MessageSyncRequest(BaseModel):
    items: list[MessageSyncItem] = Field(..., max_length=500)


class MessageResponse(BaseModel):
    id: uuid.UUID
    device_id: uuid.UUID
    sender: str
    receiver: str | None
    body: str
    message_type: str
    is_incoming: bool
    sent_at: datetime
    created_at: datetime

    model_config = {"from_attributes": True}
