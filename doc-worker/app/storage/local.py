import asyncio
import shutil
import logging

from pathlib import Path

import orjson as json
import aiofiles

from storage.base import StorageBase

logger = logging.getLogger(__name__)


class LocalStorage(StorageBase):
    def __init__(self, base_dir: str = "./cache"):
        self.base_dir = Path(base_dir).resolve()
        self.base_dir.mkdir(parents=True, exist_ok=True)

    def _get_path(self, bucket: str, key: str) -> Path:
        path = (self.base_dir / bucket / key).resolve()

        if not path.is_relative_to(self.base_dir):
            raise ValueError(
                f"Security error: Invalid key (path traversal detected): {key}"
            )

        return path

    async def download_to_file(self, bucket: str, key: str, local_path: Path) -> bool:
        source_path = self._get_path(bucket, key)
        try:
            await asyncio.to_thread(
                local_path.parent.mkdir, parents=True, exist_ok=True
            )
            await asyncio.to_thread(shutil.copy2, str(source_path), local_path)
            return True
        except Exception as e:
            logger.error(f"Failed to copy local file: {e}")
            return False

    async def upload_from_file(self, bucket: str, key: str, source_path: Path) -> bool:
        dest_path = self._get_path(bucket, key)
        try:
            await asyncio.to_thread(dest_path.parent.mkdir, parents=True, exist_ok=True)
            await asyncio.to_thread(shutil.copy2, source_path, dest_path)
            return True
        except Exception as e:
            logger.error(f"Failed to store local file: {e}")
            return False

    async def save_file(self, bucket: str, key: str, data: dict | list) -> bool:
        file_path = self._get_path(bucket, key)
        await asyncio.to_thread(file_path.parent.mkdir, parents=True, exist_ok=True)
        async with aiofiles.open(file_path, mode="wb") as f:
            await f.write(json.dumps(data, option=json.OPT_INDENT_2))

        return True

    async def load_file(self, bucket: str, key: str) -> bytes:
        file_path = self._get_path(bucket, key)
        if not await self.exists(bucket, key):
            return None
        async with aiofiles.open(file_path, mode="rb") as f:
            content = await f.read()
            try:
                return json.loads(content)
            except json.JSONDecodeError:
                raise

    async def exists(self, bucket: str, key: str) -> bool:
        file_path = self._get_path(bucket, key)
        return await asyncio.to_thread(file_path.exists)
