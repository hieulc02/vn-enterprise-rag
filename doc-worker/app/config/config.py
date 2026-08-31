from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict  # type: ignore


from llm.enums import LLMProviderType
from storage.enums import StorageType


class AppBaseSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore"
    )


class ValidationSettings(AppBaseSettings):
    model_config = SettingsConfigDict(env_prefix="VALIDATION_")

    max_concurrent: int = Field(default=2, ge=1)
    chunk_size: int = Field(default=20, ge=1)
    max_retries: int = Field(default=3, ge=0)
    base_wait_sec: int = Field(default=30, ge=1)


class ExtractionSettings(AppBaseSettings):
    model_config = SettingsConfigDict(env_prefix="EXTRACT_")

    max_concurrent: int = Field(default=2, ge=1)
    chunk_size: int = Field(default=5, ge=1)
    max_retries: int = Field(default=3, ge=0)
    base_wait_sec: int = Field(default=2, ge=1)
    version: str = Field(default="v1.1")


class GenAISettings(AppBaseSettings):
    GEN_AI_API_KEY: str = Field()
    INGESTION_MODEL_NAME: str = Field(default="gemini-3-flash-preview")
    RESOLUTION_MODEL_NAME: str = Field(default="gemini-3.1-flash-lite")


class LLamaSettings(AppBaseSettings):
    LLAMA_CLOUD_API_KEY: str = Field()


class EmbeddingSettings(AppBaseSettings):
    MODEL_NAME: str = Field(default="AITeamVN/Vietnamese_Embedding")


class KakfaSettings(AppBaseSettings):
    KAFKA_BROKER: str = Field(default="localhost:9092")
    KAFKA_CONSUMER_GROUP: str = Field(default="outbox-group")
    KAFKA_CONSUMER_TOPIC: str = Field(default="rag-cdc-topic")
    KAFKA_PRODUCER_TOPIC: str = Field(default="rag-extracting-topic")


class MinIOSettings(AppBaseSettings):
    MINIO_ENDPOINT: str = Field(default="localhost:9000")
    MINIO_ACCESS_KEY: str = Field(default="minioadmin")
    MINIO_SECRET_KEY: str = Field(default="miniopassword")
    MINIO_SECURE: bool = Field(default=False)


class AppSettings(AppBaseSettings):
    validation: ValidationSettings = Field(default_factory=ValidationSettings)
    extraction: ExtractionSettings = Field(default_factory=ExtractionSettings)

    gen_ai: GenAISettings = Field(default_factory=GenAISettings)

    llama: LLamaSettings = Field(default_factory=LLamaSettings)

    embedding: EmbeddingSettings = Field(default_factory=EmbeddingSettings)

    kafka: KakfaSettings = Field(default_factory=KakfaSettings)
    minio: MinIOSettings = Field(default_factory=MinIOSettings)

    LLM_PROVIDER: LLMProviderType = Field(default=LLMProviderType.GEN_AI)
    STORAGE_TYPE: StorageType = Field(default=StorageType.CLOUD)


@lru_cache
def get_settings() -> AppSettings:
    return AppSettings()
