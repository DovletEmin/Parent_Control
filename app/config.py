from pydantic_settings import BaseSettings, SettingsConfigDict
from functools import lru_cache


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    # Application
    app_name: str = "mSpy"
    app_env: str = "development"
    debug: bool = False
    secret_key: str = "change-me"
    api_v1_prefix: str = "/api/v1"
    cors_origins: list[str] = []

    # JWT
    jwt_access_token_expire_minutes: int = 15
    jwt_refresh_token_expire_days: int = 30

    # PostgreSQL
    postgres_user: str = "mspy"
    postgres_password: str = "mspy_secret"
    postgres_db: str = "mspy_db"
    postgres_host: str = "postgres"
    postgres_port: int = 5432

    # Redis
    redis_host: str = "redis"
    redis_port: int = 6379
    redis_db: int = 0

    # MinIO
    minio_root_user: str = "mspy_minio"
    minio_root_password: str = "mspy_minio_secret"
    minio_host: str = "minio"
    minio_port: int = 9000
    minio_bucket: str = "mspy-media"
    minio_secure: bool = False

    # FCM
    fcm_credentials_path: str = ""

    @property
    def database_url(self) -> str:
        return (
            f"postgresql+asyncpg://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
        )

    @property
    def database_url_sync(self) -> str:
        return (
            f"postgresql://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
        )

    @property
    def redis_url(self) -> str:
        return f"redis://{self.redis_host}:{self.redis_port}/{self.redis_db}"

    @property
    def minio_endpoint(self) -> str:
        return f"{self.minio_host}:{self.minio_port}"


@lru_cache
def get_settings() -> Settings:
    return Settings()
