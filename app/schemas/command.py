import uuid
from datetime import datetime

from pydantic import BaseModel, Field

from app.models.command import CommandType


class CommandCreateRequest(BaseModel):
    command_type: CommandType
    payload: str | None = Field(None, max_length=2000)


class CommandResponse(BaseModel):
    id: uuid.UUID
    device_id: uuid.UUID
    command_type: str
    payload: str | None
    status: str
    created_at: datetime
    executed_at: datetime | None

    model_config = {"from_attributes": True}


class CommandAckRequest(BaseModel):
    command_id: uuid.UUID
    status: str = Field(..., pattern="^(delivered|executed|failed)$")
