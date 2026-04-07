import uuid
from datetime import datetime, timezone

from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.command import CommandStatus, DeviceCommand
from app.models.device import Device
from app.models.user import User
from app.schemas.command import CommandAckRequest, CommandCreateRequest, CommandResponse
from app.services.device_service import DeviceService
from app.ws.manager import ws_manager


class CommandService:

    @staticmethod
    async def create_command(
        db: AsyncSession,
        user: User,
        device_id: uuid.UUID,
        data: CommandCreateRequest,
    ) -> CommandResponse:
        # Verify ownership
        await DeviceService._get_user_device(db, user, device_id)

        command = DeviceCommand(
            device_id=device_id,
            command_type=data.command_type,
            payload=data.payload,
        )
        db.add(command)
        await db.flush()

        # Try to deliver via WebSocket immediately
        ws_payload = {
            "type": "command",
            "command_id": str(command.id),
            "command_type": command.command_type,
            "payload": command.payload,
        }
        # Camera/screen commands need parent_id so the device knows who to stream to
        if data.command_type in ("request_camera", "request_screen"):
            ws_payload["parent_id"] = str(user.id)

        delivered = await ws_manager.send_to_device(device_id, ws_payload)

        if delivered:
            command.status = CommandStatus.SENT

        await db.flush()
        return CommandResponse.model_validate(command)

    @staticmethod
    async def ack_command(
        db: AsyncSession,
        device: Device,
        data: CommandAckRequest,
    ) -> None:
        result = await db.execute(
            select(DeviceCommand).where(
                DeviceCommand.id == data.command_id,
                DeviceCommand.device_id == device.id,
            )
        )
        command = result.scalar_one_or_none()

        if command is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Command not found",
            )

        command.status = data.status
        if data.status in (CommandStatus.EXECUTED, CommandStatus.FAILED):
            command.executed_at = datetime.now(timezone.utc)

        await db.flush()

        # Notify parent
        await ws_manager.send_to_parent(
            device.user_id,
            {
                "type": "command_ack",
                "command_id": str(command.id),
                "device_id": str(device.id),
                "status": data.status,
            },
        )

    @staticmethod
    async def get_pending_commands(
        db: AsyncSession, device: Device
    ) -> list[CommandResponse]:
        result = await db.execute(
            select(DeviceCommand)
            .where(
                DeviceCommand.device_id == device.id,
                DeviceCommand.status.in_([
                    CommandStatus.PENDING,
                    CommandStatus.SENT,
                ]),
            )
            .order_by(DeviceCommand.created_at.asc())
        )
        commands = result.scalars().all()
        return [CommandResponse.model_validate(c) for c in commands]

    @staticmethod
    async def get_command_history(
        db: AsyncSession,
        user: User,
        device_id: uuid.UUID,
        page: int = 1,
        page_size: int = 50,
    ) -> list[CommandResponse]:
        await DeviceService._get_user_device(db, user, device_id)

        result = await db.execute(
            select(DeviceCommand)
            .where(DeviceCommand.device_id == device_id)
            .order_by(DeviceCommand.created_at.desc())
            .offset((page - 1) * page_size)
            .limit(page_size)
        )
        commands = result.scalars().all()
        return [CommandResponse.model_validate(c) for c in commands]
