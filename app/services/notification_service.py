import json
import logging
import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.device import Device
from app.ws.manager import ws_manager

logger = logging.getLogger(__name__)


class NotificationService:
    """
    Sends push notifications to devices via FCM.
    Falls back to WebSocket if FCM is not configured.
    """

    @staticmethod
    async def notify_device(
        db: AsyncSession,
        device_id: uuid.UUID,
        title: str,
        body: str,
        data: dict | None = None,
    ) -> bool:
        """
        Send a push notification to a device.
        Returns True if delivered via WebSocket or FCM.
        """
        # Try WebSocket first (instant)
        ws_data = {
            "type": "notification",
            "title": title,
            "body": body,
            "data": data or {},
        }
        ws_sent = await ws_manager.send_to_device(device_id, ws_data)

        if ws_sent:
            return True

        # Fall back to FCM
        result = await db.execute(
            select(Device.fcm_token).where(Device.id == device_id)
        )
        fcm_token = result.scalar_one_or_none()

        if fcm_token:
            return await NotificationService._send_fcm(fcm_token, title, body, data)

        logger.warning(f"Cannot reach device {device_id}: no WS or FCM")
        return False

    @staticmethod
    async def _send_fcm(
        token: str,
        title: str,
        body: str,
        data: dict | None = None,
    ) -> bool:
        """
        Send FCM push notification.
        This is a stub — integrate with firebase-admin SDK in production.
        """
        try:
            # TODO: Implement with firebase-admin
            # from firebase_admin import messaging
            # message = messaging.Message(
            #     notification=messaging.Notification(title=title, body=body),
            #     data={k: str(v) for k, v in (data or {}).items()},
            #     token=token,
            # )
            # messaging.send(message)
            logger.info(f"FCM stub: would send to {token[:20]}... title={title}")
            return False
        except Exception as e:
            logger.error(f"FCM send error: {e}")
            return False
