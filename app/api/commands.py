import uuid

from fastapi import APIRouter, Query

from app.core.dependencies import CurrentDevice, CurrentUser, DBSession
from app.schemas.command import CommandAckRequest, CommandCreateRequest, CommandResponse
from app.services.command_service import CommandService

router = APIRouter(tags=["commands"])

# Static routes MUST be declared before parameterized routes
# to prevent {device_id} from capturing "commands" as a value.


@router.get(
    "/devices/commands/pending",
    response_model=list[CommandResponse],
    summary="Get pending commands for this device",
)
async def get_pending(device: CurrentDevice, db: DBSession):
    """Device endpoint: get pending commands to execute."""
    return await CommandService.get_pending_commands(db, device)


@router.post(
    "/devices/commands/ack",
    status_code=204,
    summary="Acknowledge command execution",
)
async def ack_command(
    data: CommandAckRequest,
    device: CurrentDevice,
    db: DBSession,
):
    """Device endpoint: acknowledge a command was delivered/executed/failed."""
    await CommandService.ack_command(db, device, data)


@router.post(
    "/devices/{device_id}/commands",
    response_model=CommandResponse,
    status_code=201,
    summary="Send a command to device",
)
async def send_command(
    device_id: uuid.UUID,
    data: CommandCreateRequest,
    user: CurrentUser,
    db: DBSession,
):
    """Parent endpoint: send a command to a child device."""
    return await CommandService.create_command(db, user, device_id, data)


@router.get(
    "/devices/{device_id}/commands",
    response_model=list[CommandResponse],
    summary="Get command history",
)
async def get_commands(
    device_id: uuid.UUID,
    user: CurrentUser,
    db: DBSession,
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
):
    """Parent endpoint: get command history for a device."""
    return await CommandService.get_command_history(db, user, device_id, page, page_size)
