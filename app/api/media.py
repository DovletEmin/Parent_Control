import uuid
from datetime import datetime

from fastapi import APIRouter, File, Form, HTTPException, Query, UploadFile, status
from fastapi.responses import Response

from app.core.dependencies import CurrentDevice, CurrentUser, CurrentUserOrToken, DBSession
from app.schemas.media import MediaListResponse, MediaUploadResponse
from app.services.device_service import DeviceService
from app.services.media_service import MediaService

router = APIRouter(tags=["media"])


@router.post(
    "/devices/media/upload",
    response_model=MediaUploadResponse,
    status_code=201,
    summary="Upload media file from device",
)
async def upload_media(
    device: CurrentDevice,
    db: DBSession,
    file: UploadFile = File(...),
    created_at_device: datetime | None = Form(None),
):
    """Device endpoint: upload a photo or video."""
    return await MediaService.upload(db, device, file, created_at_device)


@router.get(
    "/devices/{device_id}/media",
    response_model=MediaListResponse,
    summary="List media files",
)
async def list_media(
    device_id: uuid.UUID,
    user: CurrentUser,
    db: DBSession,
    file_type: str | None = Query(None),
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
):
    """Parent endpoint: list media files for a device."""
    await DeviceService.get_device(db, user, device_id)
    return await MediaService.list_media(db, device_id, file_type, page, page_size)


@router.get(
    "/media/{media_id}/download",
    summary="Download media file (redirect to presigned URL)",
)
async def download_media(
    media_id: uuid.UUID,
    user: CurrentUserOrToken,
    db: DBSession,
):
    """Parent endpoint: get a media file (proxied through backend)."""
    media = await MediaService.get_media_by_id(db, media_id)
    if media is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Media not found",
        )

    # Verify ownership through device
    await DeviceService.get_device(db, user, media.device_id)

    data = MediaService.get_file_stream(media.file_path)
    return Response(
        content=data,
        media_type=media.mime_type or "application/octet-stream",
        headers={"Cache-Control": "private, max-age=3600"},
    )


@router.get(
    "/media/{media_id}/thumbnail",
    summary="Get thumbnail (redirect to presigned URL)",
)
async def get_thumbnail(
    media_id: uuid.UUID,
    user: CurrentUserOrToken,
    db: DBSession,
):
    """Parent endpoint: get thumbnail for a media file (proxied through backend)."""
    media = await MediaService.get_media_by_id(db, media_id)
    if media is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Media not found",
        )

    await DeviceService.get_device(db, user, media.device_id)

    path = media.thumbnail_path or media.file_path
    data = MediaService.get_file_stream(path)
    content_type = media.mime_type or "application/octet-stream"
    if media.thumbnail_path:
        content_type = "image/jpeg"
    return Response(
        content=data,
        media_type=content_type,
        headers={"Cache-Control": "private, max-age=3600"},
    )
