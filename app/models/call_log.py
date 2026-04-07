import uuid
from datetime import datetime
from enum import StrEnum

from sqlalchemy import String, Integer, DateTime, ForeignKey, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class CallType(StrEnum):
    INCOMING = "incoming"
    OUTGOING = "outgoing"
    MISSED = "missed"
    REJECTED = "rejected"


class CallLog(Base):
    __tablename__ = "call_logs"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    device_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("devices.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    phone_number: Mapped[str] = mapped_column(String(50), nullable=False)
    contact_name: Mapped[str | None] = mapped_column(String(255), nullable=True)
    call_type: Mapped[str] = mapped_column(String(20), nullable=False, index=True)
    duration_seconds: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    called_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, index=True
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    device: Mapped["Device"] = relationship("Device", back_populates="call_logs")

    def __repr__(self) -> str:
        return f"<CallLog {self.call_type} {self.phone_number}>"
