import uuid
from datetime import date

from sqlalchemy import select, func as sa_func
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.app_usage import AppUsage
from app.models.device import Device
from app.schemas.app_usage import (
    AppUsageResponse,
    AppUsageStatsResponse,
    AppUsageSyncRequest,
)
from app.schemas.common import SyncResponse


class AppUsageSyncService:

    @staticmethod
    async def sync(
        db: AsyncSession, device: Device, data: AppUsageSyncRequest
    ) -> SyncResponse:
        records = [
            AppUsage(
                device_id=device.id,
                package_name=item.package_name,
                app_name=item.app_name,
                usage_seconds=item.usage_seconds,
                date=item.date,
                started_at=item.started_at,
                ended_at=item.ended_at,
            )
            for item in data.items
        ]
        db.add_all(records)
        await db.flush()
        return SyncResponse(synced=len(records))

    @staticmethod
    async def get_apps(
        db: AsyncSession,
        device_id: uuid.UUID,
        date_from: date | None = None,
        date_to: date | None = None,
        page: int = 1,
        page_size: int = 50,
    ) -> list[AppUsageResponse]:
        stmt = select(AppUsage).where(AppUsage.device_id == device_id)

        if date_from:
            stmt = stmt.where(AppUsage.date >= date_from)
        if date_to:
            stmt = stmt.where(AppUsage.date <= date_to)

        stmt = stmt.order_by(AppUsage.date.desc(), AppUsage.usage_seconds.desc())
        stmt = stmt.offset((page - 1) * page_size).limit(page_size)

        result = await db.execute(stmt)
        rows = result.scalars().all()
        return [AppUsageResponse.model_validate(r) for r in rows]

    @staticmethod
    async def get_usage_stats(
        db: AsyncSession,
        device_id: uuid.UUID,
        date_from: date | None = None,
        date_to: date | None = None,
    ) -> list[AppUsageStatsResponse]:
        stmt = (
            select(
                AppUsage.package_name,
                AppUsage.app_name,
                sa_func.sum(AppUsage.usage_seconds).label("total_seconds"),
                AppUsage.date,
            )
            .where(AppUsage.device_id == device_id)
            .group_by(AppUsage.package_name, AppUsage.app_name, AppUsage.date)
            .order_by(AppUsage.date.desc(), sa_func.sum(AppUsage.usage_seconds).desc())
        )

        if date_from:
            stmt = stmt.where(AppUsage.date >= date_from)
        if date_to:
            stmt = stmt.where(AppUsage.date <= date_to)

        result = await db.execute(stmt)
        rows = result.all()
        return [
            AppUsageStatsResponse(
                package_name=r.package_name,
                app_name=r.app_name,
                total_seconds=r.total_seconds,
                date=r.date,
            )
            for r in rows
        ]
