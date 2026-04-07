import uuid
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.call_log import CallLog
from app.models.device import Device
from app.schemas.call_log import CallLogResponse, CallLogSyncRequest
from app.schemas.common import SyncResponse


class CallLogSyncService:

    @staticmethod
    async def sync(
        db: AsyncSession, device: Device, data: CallLogSyncRequest
    ) -> SyncResponse:
        records = [
            CallLog(
                device_id=device.id,
                phone_number=item.phone_number,
                contact_name=item.contact_name,
                call_type=item.call_type,
                duration_seconds=item.duration_seconds,
                called_at=item.called_at,
            )
            for item in data.items
        ]
        db.add_all(records)
        await db.flush()
        return SyncResponse(synced=len(records))

    @staticmethod
    async def get_calls(
        db: AsyncSession,
        device_id: uuid.UUID,
        call_type: str | None = None,
        date_from: datetime | None = None,
        date_to: datetime | None = None,
        page: int = 1,
        page_size: int = 50,
    ) -> list[CallLogResponse]:
        stmt = select(CallLog).where(CallLog.device_id == device_id)

        if call_type:
            stmt = stmt.where(CallLog.call_type == call_type)
        if date_from:
            stmt = stmt.where(CallLog.called_at >= date_from)
        if date_to:
            stmt = stmt.where(CallLog.called_at <= date_to)

        stmt = stmt.order_by(CallLog.called_at.desc())
        stmt = stmt.offset((page - 1) * page_size).limit(page_size)

        result = await db.execute(stmt)
        rows = result.scalars().all()
        return [CallLogResponse.model_validate(r) for r in rows]
