import uuid
import json
import logging

from fastapi import WebSocket, WebSocketDisconnect
from sqlalchemy import select

from app.core.security import decode_token
from app.database import async_session_factory
from app.models.user import User
from app.models.device import Device
from app.ws.manager import ws_manager

logger = logging.getLogger(__name__)


async def _authenticate_parent(token: str) -> User | None:
    """Validate JWT access token and return the User."""
    payload = decode_token(token)
    if payload is None or payload.get("type") != "access":
        return None

    try:
        user_id = uuid.UUID(payload["sub"])
    except (KeyError, ValueError):
        return None

    async with async_session_factory() as db:
        result = await db.execute(select(User).where(User.id == user_id))
        user = result.scalar_one_or_none()
        if user is None or not user.is_active:
            return None
        return user


async def _get_user_device_ids(user_id: uuid.UUID) -> list[uuid.UUID]:
    async with async_session_factory() as db:
        result = await db.execute(
            select(Device.id).where(Device.user_id == user_id)
        )
        return [row[0] for row in result.all()]


async def parent_ws_handler(websocket: WebSocket, access_token: str) -> None:
    """
    WebSocket endpoint for parent dashboard.

    The parent connects with their JWT access token and receives
    real-time updates from all their devices.

    Messages FROM parent:
        - {"type": "webrtc_offer", "device_id": "...", "sdp": "..."}
        - {"type": "webrtc_ice", "device_id": "...", "candidate": "..."}
        - {"type": "webrtc_stop", "device_id": "..."}
        - {"type": "ping"}

    Messages TO parent:
        - {"type": "device_status", "device_id": "...", "status": "online|offline"}
        - {"type": "device_location", "device_id": "...", ...}
        - {"type": "command_ack", "command_id": "...", ...}
        - {"type": "webrtc_answer", "device_id": "...", "sdp": "..."}
        - {"type": "webrtc_ice", "device_id": "...", "candidate": "..."}
        - {"type": "pong"}
    """
    user = await _authenticate_parent(access_token)
    if user is None:
        await websocket.close(code=4001, reason="Invalid token")
        return

    await ws_manager.connect_parent(user.id, websocket)

    # Send current device statuses
    device_ids = await _get_user_device_ids(user.id)
    for device_id in device_ids:
        is_online = ws_manager.is_device_connected(device_id)
        await websocket.send_json({
            "type": "device_status",
            "device_id": str(device_id),
            "status": "online" if is_online else "offline",
        })

    try:
        while True:
            raw = await websocket.receive_text()
            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                continue

            msg_type = data.get("type")

            if msg_type == "ping":
                await websocket.send_json({"type": "pong"})

            elif msg_type == "webrtc_offer":
                device_id_str = data.get("device_id")
                if device_id_str:
                    try:
                        device_id = uuid.UUID(device_id_str)
                    except ValueError:
                        continue
                    # Verify parent owns this device
                    if device_id in device_ids:
                        await ws_manager.send_to_device(
                            device_id,
                            {
                                "type": "webrtc_offer",
                                "parent_id": str(user.id),
                                "sdp": data.get("sdp"),
                            },
                        )

            elif msg_type == "webrtc_ice":
                device_id_str = data.get("device_id")
                if device_id_str:
                    try:
                        device_id = uuid.UUID(device_id_str)
                    except ValueError:
                        continue
                    if device_id in device_ids:
                        await ws_manager.send_to_device(
                            device_id,
                            {
                                "type": "webrtc_ice",
                                "parent_id": str(user.id),
                                "candidate": data.get("candidate"),
                            },
                        )

            elif msg_type == "webrtc_stop":
                device_id_str = data.get("device_id")
                if device_id_str:
                    try:
                        device_id = uuid.UUID(device_id_str)
                    except ValueError:
                        continue
                    if device_id in device_ids:
                        await ws_manager.send_to_device(
                            device_id,
                            {
                                "type": "webrtc_stop",
                            },
                        )

            elif msg_type == "screen_offer":
                device_id_str = data.get("device_id")
                if device_id_str:
                    try:
                        device_id = uuid.UUID(device_id_str)
                    except ValueError:
                        continue
                    if device_id in device_ids:
                        await ws_manager.send_to_device(
                            device_id,
                            {
                                "type": "screen_offer",
                                "parent_id": str(user.id),
                                "sdp": data.get("sdp"),
                            },
                        )

            elif msg_type == "screen_ice":
                device_id_str = data.get("device_id")
                if device_id_str:
                    try:
                        device_id = uuid.UUID(device_id_str)
                    except ValueError:
                        continue
                    if device_id in device_ids:
                        await ws_manager.send_to_device(
                            device_id,
                            {
                                "type": "screen_ice",
                                "parent_id": str(user.id),
                                "candidate": data.get("candidate"),
                            },
                        )

            elif msg_type == "screen_stop":
                device_id_str = data.get("device_id")
                if device_id_str:
                    try:
                        device_id = uuid.UUID(device_id_str)
                    except ValueError:
                        continue
                    if device_id in device_ids:
                        await ws_manager.send_to_device(
                            device_id,
                            {
                                "type": "screen_stop",
                            },
                        )

    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.error(f"Parent WS error for {user.id}: {e}")
    finally:
        await ws_manager.disconnect_parent(user.id, websocket)
