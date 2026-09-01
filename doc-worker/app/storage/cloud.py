import io
import logging
import asyncio

import orjson as json
from minio import Minio
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
            response = None
            try:
                response = self.storage.get_object(bucket, key)
                with open(path, "wb") as file_path:
                    for byte in response.stream():
                        file_path.write(byte)
            finally:
                if response:
                    response.close()
                    response.release_conn()

        return await asyncio.to_thread(_download_stream_sync)

    async def upload_from_file(self, bucket: str, key: str, path: Path):
        def _upload_file_sync():
            self.storage.fput_object(bucket, key, path)

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

    async def load_file(self, bucket: str, key: str) -> bytes:
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
                logger.error(f"File {key} in {bucket} does not exist: {e}")
                return False

            logger.info(f"File {key} in {bucket} does not exist")
            return False

        return await asyncio.to_thread(_exists_file_sync)
