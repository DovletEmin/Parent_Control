import uuid
import json
import logging
from datetime import datetime, timezone

from fastapi import WebSocket, WebSocketDisconnect
from sqlalchemy import select, update

from app.database import async_session_factory
from app.models.device import Device
from app.models.command import DeviceCommand, CommandStatus
from app.models.location import Location
from app.ws.manager import ws_manager

logger = logging.getLogger(__name__)


async def _authenticate_device(token: str) -> Device | None:
    """Validate device token and return the Device."""
    async with async_session_factory() as db:
        result = await db.execute(
            select(Device).where(
                Device.device_token == token,
                Device.is_paired.is_(True),
            )
        )
        return result.scalar_one_or_none()


async def _set_device_online(device_id: uuid.UUID, online: bool) -> None:
    async with async_session_factory() as db:
        await db.execute(
            update(Device)
            .where(Device.id == device_id)
            .values(
                is_online=online,
                last_seen_at=datetime.now(timezone.utc),
            )
        )
        await db.commit()


async def device_ws_handler(websocket: WebSocket, device_token: str) -> None:
    """
    WebSocket endpoint for child devices.

    The device connects, authenticates via device_token in the URL,
    and then listens for commands while sending status updates.

    Messages FROM device:
        - {"type": "heartbeat"}
        - {"type": "command_ack", "command_id": "...", "status": "executed|failed"}
        - {"type": "location", "latitude": ..., "longitude": ..., ...}
        - {"type": "webrtc_answer", "sdp": "..."}
        - {"type": "webrtc_ice", "candidate": "..."}

    Messages TO device:
        - {"type": "command", "command_id": "...", "command_type": "...", "payload": "..."}
        - {"type": "webrtc_offer", "sdp": "...", "parent_id": "..."}
        - {"type": "webrtc_ice", "candidate": "...", "parent_id": "..."}
    """
    device = await _authenticate_device(device_token)
    if device is None:
        await websocket.close(code=4001, reason="Invalid device token")
        return

    await ws_manager.connect_device(device.id, websocket)
    await _set_device_online(device.id, True)

    # Notify parent that device came online
    await ws_manager.send_to_parent(
        device.user_id,
        {
            "type": "device_status",
            "device_id": str(device.id),
            "status": "online",
        },
    )

    # Deliver any pending commands
    async with async_session_factory() as db:
        result = await db.execute(
            select(DeviceCommand).where(
                DeviceCommand.device_id == device.id,
                DeviceCommand.status == CommandStatus.PENDING,
            )
        )
        pending = result.scalars().all()
        for cmd in pending:
            await ws_manager.send_to_device(
                device.id,
                {
                    "type": "command",
                    "command_id": str(cmd.id),
                    "command_type": cmd.command_type,
                    "payload": cmd.payload,
                },
            )
            cmd.status = CommandStatus.SENT
        await db.commit()

    try:
        while True:
            raw = await websocket.receive_text()
            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                continue

            msg_type = data.get("type")

            if msg_type == "heartbeat":
                await _set_device_online(device.id, True)

            elif msg_type == "command_ack":
                command_id = data.get("command_id")
                ack_status = data.get("status", "executed")
                if command_id:
                    try:
                        cmd_uuid = uuid.UUID(command_id)
                    except ValueError:
                        continue
                    async with async_session_factory() as db:
                        executed_at = (
                            datetime.now(timezone.utc)
                            if ack_status in ("executed", "failed")
                            else None
                        )
                        await db.execute(
                            update(DeviceCommand)
                            .where(DeviceCommand.id == cmd_uuid)
                            .values(
                                status=ack_status,
                                executed_at=executed_at,
                            )
                        )
                        await db.commit()

                    await ws_manager.send_to_parent(
                        device.user_id,
                        {
                            "type": "command_ack",
                            "command_id": command_id,
                            "device_id": str(device.id),
                            "status": ack_status,
                        },
                    )

            elif msg_type == "location":
                lat = data.get("latitude")
                lon = data.get("longitude")
                recorded_at_str = data.get("recorded_at")

                # Save to database
                if lat is not None and lon is not None:
                    try:
                        recorded_at = (
                            datetime.fromisoformat(recorded_at_str)
                            if recorded_at_str
                            else datetime.now(timezone.utc)
                        )
                        async with async_session_factory() as db:
                            db.add(Location(
                                device_id=device.id,
                                latitude=lat,
                                longitude=lon,
                                accuracy=data.get("accuracy"),
                                altitude=data.get("altitude"),
                                speed=data.get("speed"),
                                recorded_at=recorded_at,
                            ))
                            await db.commit()
                    except Exception as e:
                        logger.warning(f"Failed to save WS location: {e}")

                # Forward real-time location to parent
                await ws_manager.send_to_parent(
                    device.user_id,
                    {
                        "type": "device_location",
                        "device_id": str(device.id),
                        "latitude": lat,
                        "longitude": lon,
                        "accuracy": data.get("accuracy"),
                        "recorded_at": recorded_at_str,
                    },
                )

            elif msg_type in ("webrtc_answer", "webrtc_ice"):
                # Forward WebRTC signaling to parent
                parent_id = data.get("parent_id")
                if parent_id:
                    try:
                        parent_uuid = uuid.UUID(parent_id)
                    except ValueError:
                        continue
                    await ws_manager.send_to_parent(
                        parent_uuid,
                        {
                            "type": msg_type,
                            "device_id": str(device.id),
                            **{k: v for k, v in data.items() if k not in ("type", "parent_id")},
                        },
                    )

    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.error(f"Device WS error for {device.id}: {e}")
    finally:
        await ws_manager.disconnect_device(device.id)
        await _set_device_online(device.id, False)

        await ws_manager.send_to_parent(
            device.user_id,
            {
                "type": "device_status",
                "device_id": str(device.id),
                "status": "offline",
            },
        )
