from fastapi import APIRouter

from app.core.dependencies import CurrentUser, DBSession
from app.schemas.auth import (
    AuthResponse,
    LoginRequest,
    RefreshRequest,
    RegisterRequest,
    TokenResponse,
    UserResponse,
)
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=AuthResponse, status_code=201)
async def register(data: RegisterRequest, db: DBSession):
    return await AuthService.register(db, data)


@router.post("/login", response_model=AuthResponse)
async def login(data: LoginRequest, db: DBSession):
    return await AuthService.login(db, data)


@router.post("/refresh", response_model=TokenResponse)
async def refresh(data: RefreshRequest, db: DBSession):
    return await AuthService.refresh(db, data)


@router.get("/me", response_model=UserResponse)
async def me(user: CurrentUser):
    return UserResponse.model_validate(user)
