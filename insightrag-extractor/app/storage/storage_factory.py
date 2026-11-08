from config.config import AppSettings
from storage.enums import StorageType
from storage.base import StorageBase


def get_storage(settings: AppSettings) -> StorageBase:
    match settings.STORAGE_TYPE:
        case StorageType.LOCAL:
            from storage.local import LocalStorage

            return LocalStorage(base_dir="cache")
        case StorageType.CLOUD:
            from storage.cloud import MinioStorage

            minio_settings = settings.minio

            return MinioStorage(minio_settings)
        case _:
            raise ValueError(f"Unsupported Storage type: {settings.STORAGE_TYPE}")
