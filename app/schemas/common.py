from pydantic import BaseModel


class PaginationParams(BaseModel):
    page: int = 1
    page_size: int = 50


class SyncResponse(BaseModel):
    synced: int
    message: str = "Sync completed"
