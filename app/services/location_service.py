import uuid
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.location import Location
from app.models.device import Device
from app.schemas.location import (
    LocationLatestResponse,
    LocationResponse,
    LocationSyncRequest,
)
from app.schemas.common import SyncResponse


class LocationSyncService:

    @staticmethod
    async def sync(
        db: AsyncSession, device: Device, data: LocationSyncRequest
    ) -> SyncResponse:
        records = [
            Location(
                device_id=device.id,
                latitude=item.latitude,
                longitude=item.longitude,
                accuracy=item.accuracy,
                altitude=item.altitude,
                speed=item.speed,
                recorded_at=item.recorded_at,
            )
            for item in data.items
        ]
        db.add_all(records)
        await db.flush()
        return SyncResponse(synced=len(records))

    @staticmethod
    async def get_locations(
        db: AsyncSession,
        device_id: uuid.UUID,
        date_from: datetime | None = None,
        date_to: datetime | None = None,
        page: int = 1,
        page_size: int = 100,
    ) -> list[LocationResponse]:
        stmt = select(Location).where(Location.device_id == device_id)

        if date_from:
            stmt = stmt.where(Location.recorded_at >= date_from)
        if date_to:
            stmt = stmt.where(Location.recorded_at <= date_to)

        stmt = stmt.order_by(Location.recorded_at.desc())
        stmt = stmt.offset((page - 1) * page_size).limit(page_size)

        result = await db.execute(stmt)
        rows = result.scalars().all()
        return [LocationResponse.model_validate(r) for r in rows]

    @staticmethod
    async def get_latest(
        db: AsyncSession, device_id: uuid.UUID
    ) -> LocationLatestResponse | None:
        stmt = (
            select(Location)
            .where(Location.device_id == device_id)
            .order_by(Location.recorded_at.desc())
            .limit(1)
        )
        result = await db.execute(stmt)
        loc = result.scalar_one_or_none()

        if loc is None:
            return None

        return LocationLatestResponse(
            latitude=loc.latitude,
            longitude=loc.longitude,
            accuracy=loc.accuracy,
            altitude=loc.altitude,
            speed=loc.speed,
            recorded_at=loc.recorded_at,
        )
