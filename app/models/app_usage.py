import uuid
from datetime import date, datetime

from sqlalchemy import String, Integer, Date, DateTime, ForeignKey, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class AppUsage(Base):
    __tablename__ = "app_usage"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    device_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("devices.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    package_name: Mapped[str] = mapped_column(String(255), nullable=False, index=True)
    app_name: Mapped[str] = mapped_column(String(255), nullable=False)
    usage_seconds: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    date: Mapped[date] = mapped_column(Date, nullable=False, index=True)
    started_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    ended_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    device: Mapped["Device"] = relationship("Device", back_populates="app_usages")

    def __repr__(self) -> str:
        return f"<AppUsage app={self.app_name} seconds={self.usage_seconds}>"
