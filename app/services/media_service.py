import io
import uuid
from datetime import datetime, timezone

from fastapi import HTTPException, UploadFile, status
from minio import Minio
from minio.error import S3Error
from sqlalchemy import select, func as sa_func
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.models.device import Device
from app.models.media import MediaFile, FileType
from app.schemas.media import MediaListResponse, MediaUploadResponse

settings = get_settings()

ALLOWED_MIME_TYPES = {
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/heic",
    "video/mp4",
    "video/3gpp",
    "video/quicktime",
}

MAX_FILE_SIZE = 100 * 1024 * 1024  # 100 MB


def _get_minio_client() -> Minio:
    return Minio(
        endpoint=settings.minio_endpoint,
        access_key=settings.minio_root_user,
        secret_key=settings.minio_root_password,
        secure=settings.minio_secure,
    )


def _ensure_bucket(client: Minio) -> None:
    if not client.bucket_exists(settings.minio_bucket):
        client.make_bucket(settings.minio_bucket)


class MediaService:

    @staticmethod
    async def upload(
        db: AsyncSession,
        device: Device,
        file: UploadFile,
        created_at_device: datetime | None = None,
    ) -> MediaUploadResponse:
        if file.content_type not in ALLOWED_MIME_TYPES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Unsupported file type: {file.content_type}",
            )

        content = await file.read()
        file_size = len(content)

        if file_size > MAX_FILE_SIZE:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail="File too large (max 100MB)",
            )

        if file_size == 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Empty file",
            )

        file_type = (
            FileType.VIDEO
            if file.content_type.startswith("video/")
            else FileType.PHOTO
        )

        ext = ""
        if file.filename and "." in file.filename:
            ext = "." + file.filename.rsplit(".", 1)[1].lower()

        object_name = f"{device.id}/{file_type}/{uuid.uuid4().hex}{ext}"

        client = _get_minio_client()
        _ensure_bucket(client)

        try:
            client.put_object(
                bucket_name=settings.minio_bucket,
                object_name=object_name,
                data=io.BytesIO(content),
                length=file_size,
                content_type=file.content_type,
            )
        except S3Error as e:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to upload file: {e}",
            )

        media = MediaFile(
            device_id=device.id,
            file_type=file_type,
            file_path=object_name,
            original_filename=file.filename,
            file_size=file_size,
            mime_type=file.content_type,
            created_at_device=created_at_device,
        )
        db.add(media)
        await db.flush()

        return MediaUploadResponse.model_validate(media)

    @staticmethod
    async def list_media(
        db: AsyncSession,
        device_id: uuid.UUID,
        file_type: str | None = None,
        page: int = 1,
        page_size: int = 50,
    ) -> MediaListResponse:
        base_filter = MediaFile.device_id == device_id
        filters = [base_filter]

        if file_type:
            filters.append(MediaFile.file_type == file_type)

        # Count
        count_stmt = select(sa_func.count(MediaFile.id)).where(*filters)
        total = (await db.execute(count_stmt)).scalar() or 0

        # Items
        stmt = (
            select(MediaFile)
            .where(*filters)
            .order_by(MediaFile.uploaded_at.desc())
            .offset((page - 1) * page_size)
            .limit(page_size)
        )
        result = await db.execute(stmt)
        items = [MediaUploadResponse.model_validate(r) for r in result.scalars().all()]

        return MediaListResponse(
            items=items,
            total=total,
            page=page,
            page_size=page_size,
        )

    @staticmethod
    def get_download_url(file_path: str, expires_hours: int = 1) -> str:
        from datetime import timedelta

        client = _get_minio_client()
        try:
            url = client.presigned_get_object(
                bucket_name=settings.minio_bucket,
                object_name=file_path,
                expires=timedelta(hours=expires_hours),
            )
            return url
        except S3Error as e:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"File not found: {e}",
            )

    @staticmethod
    def get_file_stream(file_path: str):
        """Get file data from MinIO as bytes + content type."""
        client = _get_minio_client()
        try:
            response = client.get_object(
                bucket_name=settings.minio_bucket,
                object_name=file_path,
            )
            data = response.read()
            response.close()
            response.release_conn()
            return data
        except S3Error as e:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"File not found: {e}",
            )

    @staticmethod
    async def get_media_by_id(
        db: AsyncSession, media_id: uuid.UUID
    ) -> MediaFile | None:
        result = await db.execute(
            select(MediaFile).where(MediaFile.id == media_id)
        )
        return result.scalar_one_or_none()
