import uuid
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.message import Message
from app.models.device import Device
from app.schemas.message import MessageResponse, MessageSyncRequest
from app.schemas.common import SyncResponse


class MessageSyncService:

    @staticmethod
    async def sync(
        db: AsyncSession, device: Device, data: MessageSyncRequest
    ) -> SyncResponse:
        records = [
            Message(
                device_id=device.id,
                sender=item.sender,
                receiver=item.receiver,
                body=item.body,
                message_type=item.message_type,
                is_incoming=item.is_incoming,
                sent_at=item.sent_at,
            )
            for item in data.items
        ]
        db.add_all(records)
        await db.flush()
        return SyncResponse(synced=len(records))

    @staticmethod
    async def get_messages(
        db: AsyncSession,
        device_id: uuid.UUID,
        message_type: str | None = None,
        contact: str | None = None,
        date_from: datetime | None = None,
        date_to: datetime | None = None,
        page: int = 1,
        page_size: int = 50,
    ) -> list[MessageResponse]:
        stmt = select(Message).where(Message.device_id == device_id)

        if message_type:
            stmt = stmt.where(Message.message_type == message_type)
        if contact:
            escaped = contact.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            stmt = stmt.where(
                (Message.sender.ilike(f"%{escaped}%", escape="\\"))
                | (Message.receiver.ilike(f"%{escaped}%", escape="\\"))
            )
        if date_from:
            stmt = stmt.where(Message.sent_at >= date_from)
        if date_to:
            stmt = stmt.where(Message.sent_at <= date_to)

        stmt = stmt.order_by(Message.sent_at.desc())
        stmt = stmt.offset((page - 1) * page_size).limit(page_size)

        result = await db.execute(stmt)
        rows = result.scalars().all()
        return [MessageResponse.model_validate(r) for r in rows]
