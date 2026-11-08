import io
import logging
import asyncio
from typing import Any

import orjson as json

from minio import Minio
from minio.deleteobjects import DeleteObject
from minio.error import S3Error
from pathlib import Path
from config.config import MinIOSettings

logger = logging.getLogger(__name__)

from storage.base import StorageBase


class MinioStorage(StorageBase):
    def __init__(self, config: MinIOSettings):
        self.storage = Minio(
            endpoint=config.MINIO_ENDPOINT,
            access_key=config.MINIO_ACCESS_KEY,
            secret_key=config.MINIO_SECRET_KEY,
            secure=config.MINIO_SECURE,
        )

    async def download_to_file(self, bucket: str, key: str, path: Path):
        def _download_stream_sync():
            self.storage.fget_object(bucket, key, str(path))

        return await asyncio.to_thread(_download_stream_sync)

    async def upload_from_file(self, bucket: str, key: str, path: Path):
        def _upload_file_sync():
            self.storage.fput_object(bucket, key, str(path))

        return await asyncio.to_thread(_upload_file_sync)

    async def save_file(self, bucket: str, key: str, data: dict | list):
        def _save_file_sync():
            json_bytes = json.dumps(data, option=json.OPT_INDENT_2)
            data_stream = io.BytesIO(json_bytes)
            self.storage.put_object(
                bucket_name=bucket,
                object_name=key,
                data=data_stream,
                length=len(json_bytes),
                content_type="application/json",
            )
            return True

        return await asyncio.to_thread(_save_file_sync)

    async def load_file(self, bucket: str, key: str) -> Any:
        def _load_file_sync():
            response = None
            try:
                response = self.storage.get_object(bucket, key)
                return json.loads(response.read())
            except json.JSONDecodeError:
                raise
            finally:
                if response:
                    response.close()
                    response.release_conn()

        return await asyncio.to_thread(_load_file_sync)

    async def exists(self, bucket: str, key: str) -> bool:
        def _exists_file_sync():
            try:
                if self.storage.stat_object(bucket, key):
                    return True
            except S3Error as e:
                if e.code == "NoSuchKey":
                    logger.info(f"File {key} in {bucket} does not exist: {e}")
                    return False

                logger.error(f"Unexpected error checking {key} in bucket {bucket}: {e}")
                raise

        return await asyncio.to_thread(_exists_file_sync)

    async def delete_target(self, bucket: str, target_path: str) -> bool:
        def _delete_sync():
            try:
                objects_to_delete = self.storage.list_objects(
                    bucket_name=bucket, prefix=target_path, recursive=True
                )
                delete_list = (
                    DeleteObject(obj.object_name) for obj in objects_to_delete
                )
                errors = self.storage.remove_objects(bucket, delete_list)
                error_list = [error for error in errors]

                if error_list:
                    logger.error(
                        f"Failed to delete {target_path} in {bucket}. Errors: {error_list}"
                    )
                    return False

                return True

            except S3Error as e:
                logger.error(f"Failed to delete target {target_path} in {bucket}: {e}")
                raise

        return await asyncio.to_thread(_delete_sync)
