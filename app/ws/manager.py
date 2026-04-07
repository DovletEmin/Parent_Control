import uuid
from dataclasses import dataclass, field

from fastapi import WebSocket


@dataclass
class ConnectionManager:
    """Manages WebSocket connections for devices and parent dashboards."""

    # device_id -> WebSocket
    device_connections: dict[uuid.UUID, WebSocket] = field(default_factory=dict)
    # user_id -> list[WebSocket] (parent can have multiple tabs)
    parent_connections: dict[uuid.UUID, list[WebSocket]] = field(default_factory=dict)

    async def connect_device(self, device_id: uuid.UUID, ws: WebSocket) -> None:
        await ws.accept()
        self.device_connections[device_id] = ws

    async def disconnect_device(self, device_id: uuid.UUID) -> None:
        self.device_connections.pop(device_id, None)

    async def connect_parent(self, user_id: uuid.UUID, ws: WebSocket) -> None:
        await ws.accept()
        if user_id not in self.parent_connections:
            self.parent_connections[user_id] = []
        self.parent_connections[user_id].append(ws)

    async def disconnect_parent(self, user_id: uuid.UUID, ws: WebSocket) -> None:
        if user_id in self.parent_connections:
            self.parent_connections[user_id] = [
                c for c in self.parent_connections[user_id] if c is not ws
            ]
            if not self.parent_connections[user_id]:
                del self.parent_connections[user_id]

    async def send_to_device(self, device_id: uuid.UUID, data: dict) -> bool:
        ws = self.device_connections.get(device_id)
        if ws is None:
            return False
        await ws.send_json(data)
        return True

    async def send_to_parent(self, user_id: uuid.UUID, data: dict) -> None:
        for ws in self.parent_connections.get(user_id, []):
            await ws.send_json(data)

    def is_device_connected(self, device_id: uuid.UUID) -> bool:
        return device_id in self.device_connections


ws_manager = ConnectionManager()
