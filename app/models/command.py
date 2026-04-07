import uuid
from datetime import datetime
from enum import StrEnum

from sqlalchemy import String, Text, DateTime, ForeignKey, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class CommandType(StrEnum):
    BLOCK_APP = "block_app"
    UNBLOCK_APP = "unblock_app"
    REQUEST_CAMERA = "request_camera"
    REQUEST_SCREEN = "request_screen"
    REQUEST_LOCATION = "request_location"
    PLAY_SOUND = "play_sound"
    LOCK_DEVICE = "lock_device"
    SYNC_NOW = "sync_now"


class CommandStatus(StrEnum):
    PENDING = "pending"
    SENT = "sent"
    DELIVERED = "delivered"
    EXECUTED = "executed"
    FAILED = "failed"


class DeviceCommand(Base):
    __tablename__ = "device_commands"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    device_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("devices.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    command_type: Mapped[str] = mapped_column(String(50), nullable=False, index=True)
    payload: Mapped[str | None] = mapped_column(Text, nullable=True)
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, default=CommandStatus.PENDING, index=True
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    executed_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    device: Mapped["Device"] = relationship("Device", back_populates="commands")

    def __repr__(self) -> str:
        return f"<DeviceCommand type={self.command_type} status={self.status}>"
