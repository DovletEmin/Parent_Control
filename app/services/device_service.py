import uuid
from datetime import datetime, timezone

from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import generate_device_token, generate_pairing_code
from app.models.device import Device
from app.models.user import User
from app.schemas.device import (
    DeviceCreateRequest,
    DevicePairRequest,
    DevicePairResponse,
    DeviceResponse,
    DeviceUpdateFCMRequest,
    DeviceWithPairingCodeResponse,
)


class DeviceService:

    @staticmethod
    async def create_device(
        db: AsyncSession, user: User, data: DeviceCreateRequest
    ) -> DeviceWithPairingCodeResponse:
        pairing_code = generate_pairing_code()

        # Ensure unique pairing code
        for _ in range(10):
            result = await db.execute(
                select(Device).where(Device.pairing_code == pairing_code)
            )
            if result.scalar_one_or_none() is None:
                break
            pairing_code = generate_pairing_code()

        device = Device(
            user_id=user.id,
            name=data.name,
            pairing_code=pairing_code,
        )
        db.add(device)
        await db.flush()

        return DeviceWithPairingCodeResponse.model_validate(device)

    @staticmethod
    async def pair_device(
        db: AsyncSession, data: DevicePairRequest
    ) -> DevicePairResponse:
        result = await db.execute(
            select(Device).where(
                Device.pairing_code == data.pairing_code,
                Device.is_paired.is_(False),
            )
        )
        device = result.scalar_one_or_none()

        if device is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Invalid or already used pairing code",
            )

        device_token = generate_device_token()

        device.device_token = device_token
        device.is_paired = True
        device.pairing_code = None  # Invalidate after use
        device.device_model = data.device_model
        device.android_version = data.android_version
        device.fcm_token = data.fcm_token
        device.last_seen_at = datetime.now(timezone.utc)

        await db.flush()

        return DevicePairResponse(
            device_id=device.id,
            device_token=device_token,
        )

    @staticmethod
    async def get_user_devices(
        db: AsyncSession, user: User
    ) -> list[DeviceResponse]:
        result = await db.execute(
            select(Device)
            .where(Device.user_id == user.id)
            .order_by(Device.created_at.desc())
        )
        devices = result.scalars().all()
        return [DeviceResponse.model_validate(d) for d in devices]

    @staticmethod
    async def get_device(
        db: AsyncSession, user: User, device_id: uuid.UUID
    ) -> DeviceResponse:
        device = await DeviceService._get_user_device(db, user, device_id)
        return DeviceResponse.model_validate(device)

    @staticmethod
    async def delete_device(
        db: AsyncSession, user: User, device_id: uuid.UUID
    ) -> None:
        device = await DeviceService._get_user_device(db, user, device_id)
        await db.delete(device)
        await db.flush()


    @staticmethod
    async def update_fcm_token(
        db: AsyncSession, device: Device, data: DeviceUpdateFCMRequest
    ) -> None:
        device.fcm_token = data.fcm_token
        await db.flush()

    @staticmethod
    async def heartbeat(db: AsyncSession, device: Device) -> None:
        device.is_online = True
        device.last_seen_at = datetime.now(timezone.utc)
        await db.flush()

    @staticmethod
    async def _get_user_device(
        db: AsyncSession, user: User, device_id: uuid.UUID
    ) -> Device:
        result = await db.execute(
            select(Device).where(
                Device.id == device_id,
                Device.user_id == user.id,
            )
        )
        device = result.scalar_one_or_none()

        if device is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Device not found",
            )

        return device
