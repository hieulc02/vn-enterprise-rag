from abc import ABC, abstractmethod
from pathlib import Path


class StorageBase(ABC):

    @abstractmethod
    async def download_to_file(self, bucket: str, key: str, path: Path):
        pass

    @abstractmethod
    async def upload_from_file(self, bucket: str, key: str, path: Path):
        pass

    @abstractmethod
    async def save_file(self, bucket: str, key: str, data: dict | list):
        pass

    @abstractmethod
    async def load_file(self, bucket: str, key: str):
        pass

    @abstractmethod
    async def exists(self, bucket: str, key: str) -> bool:
        pass
