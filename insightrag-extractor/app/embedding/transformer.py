import asyncio
import numpy as np

from config.config import AppSettings
from sentence_transformers import SentenceTransformer


class Embedder:

    def __init__(self, settings: AppSettings):
        self.model = settings.embedding.MODEL_NAME
        self._embedder = SentenceTransformer(self.model)

    def embed(
        self, text: str | list[str], batch_size: int = 32, norm_embedding: bool = False
    ) -> np.ndarray:
        return self._embedder.encode(
            text, batch_size=batch_size, normalize_embeddings=norm_embedding
        )

    async def aembed(
        self, text: str | list[str], batch_size: int = 32, norm_embedding: bool = False
    ) -> np.ndarray:
        return await asyncio.to_thread(self.embed, text, batch_size, norm_embedding)
