import uuid
from datetime import datetime

from pydantic import BaseModel

from app.models.media import FileType


class MediaUploadResponse(BaseModel):
    id: uuid.UUID
    device_id: uuid.UUID
    file_type: str
    file_path: str
    thumbnail_path: str | None
    original_filename: str | None
    file_size: int
    mime_type: str | None
    created_at_device: datetime | None
    uploaded_at: datetime

    model_config = {"from_attributes": True}


class MediaListResponse(BaseModel):
    items: list[MediaUploadResponse]
    total: int
    page: int
    page_size: int
