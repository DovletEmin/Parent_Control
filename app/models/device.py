import uuid
from datetime import datetime

from sqlalchemy import String, Boolean, DateTime, ForeignKey, func, Integer
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Device(Base):
    __tablename__ = "devices"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    device_model: Mapped[str | None] = mapped_column(String(100), nullable=True)
    android_version: Mapped[str | None] = mapped_column(String(20), nullable=True)
    pairing_code: Mapped[str | None] = mapped_column(
        String(6), nullable=True, unique=True, index=True
    )
    device_token: Mapped[str | None] = mapped_column(
        String(255), nullable=True, unique=True, index=True
    )
    fcm_token: Mapped[str | None] = mapped_column(String(512), nullable=True)
    is_online: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    is_paired: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    last_seen_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )

    user: Mapped["User"] = relationship("User", back_populates="devices")

    app_usages: Mapped[list["AppUsage"]] = relationship(
        "AppUsage", back_populates="device", cascade="all, delete-orphan", lazy="noload"
    )
    call_logs: Mapped[list["CallLog"]] = relationship(
        "CallLog", back_populates="device", cascade="all, delete-orphan", lazy="noload"
    )
    messages: Mapped[list["Message"]] = relationship(
        "Message", back_populates="device", cascade="all, delete-orphan", lazy="noload"
    )
    media_files: Mapped[list["MediaFile"]] = relationship(
        "MediaFile", back_populates="device", cascade="all, delete-orphan", lazy="noload"
    )
    locations: Mapped[list["Location"]] = relationship(
        "Location", back_populates="device", cascade="all, delete-orphan", lazy="noload"
    )
    commands: Mapped[list["DeviceCommand"]] = relationship(
        "DeviceCommand", back_populates="device", cascade="all, delete-orphan", lazy="noload"
    )

    def __repr__(self) -> str:
        return f"<Device id={self.id} name={self.name}>"
