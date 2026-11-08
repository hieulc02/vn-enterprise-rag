import os
import stat
import asyncio
import hashlib
import logging
from typing import Any

import orjson as json

from llm.base import BaseLLMProvider
from config.config import ValidationSettings

from diskcache import Cache, JSONDisk

logger = logging.getLogger(__name__)


class DocumentValidation:

    def __init__(self, llm_provider: "BaseLLMProvider", config: ValidationSettings):
        self.llm = llm_provider
        self.config = config
        self.semaphore = asyncio.Semaphore(self.config.max_concurrent)
        self.batch_size = self.config.batch_size

        cache_dir = self.config.cached_dir

        os.makedirs(cache_dir, exist_ok=True)
        os.chmod(cache_dir, stat.S_IRWXU)

        self.cache = Cache(
            directory=cache_dir, disk=JSONDisk, eviction_policy="least-recently-used"
        )

    def _generate_cache_key(self, prompt: str, item: dict) -> str:
        content_hash = {k: v for k, v in item.items() if k != "id"}
        string_key = f"{prompt}::{json.dumps(content_hash, option=json.OPT_SORT_KEYS).decode('utf-8')}"
        return hashlib.sha256(string_key.encode()).hexdigest()

    async def _validate_chunk(self, prompt: str, chunk: list[dict]) -> dict:
        for attempt in range(self.config.max_retries):
            try:
                async with self.semaphore:
                    return await self.llm.generate_json(
                        prompt=prompt,
                        temperature=0.0,
                        contents=json.dumps(chunk).decode("utf-8"),
                    )
            except Exception as e:
                if attempt == self.config.max_retries - 1:
                    logger.error(f"Document validation failed: {e}")
                    return {item["id"]: True for item in chunk}

                wait_time = self.config.base_wait_sec ** (attempt + 1)
                logger.warning(
                    f"Retry LLM API calling {attempt + 1}/{self.config.max_retries}"
                )
                await asyncio.sleep(wait_time)
        return {}

    async def validate_all(
        self, prompt: str, validate_data: list[dict]
    ) -> dict[str, Any]:
        if not validate_data:
            return {}

        valid_data = {}
        cache_misses: list[dict] = []
        cache_item_key_map = {}

        for item in validate_data:
            cache_key = self._generate_cache_key(prompt, item)
            cached_result = self.cache.get(cache_key)
            if cached_result is not None:
                valid_data[item["id"]] = cached_result
            else:
                cache_misses.append(item)
                cache_item_key_map[item["id"]] = cache_key

        if cache_misses:
            logger.info(
                f"Cache misses for {len(cache_misses)}/{len(validate_data)} items. Validating with LLM..."
            )
            chunks = [
                cache_misses[i : i + self.batch_size]
                for i in range(0, len(cache_misses), self.batch_size)
            ]
            tasks = [self._validate_chunk(prompt, chunk) for chunk in chunks]
            results = await asyncio.gather(*tasks)

            for result in results:
                if isinstance(result, dict):
                    valid_data.update(result)

                    for item_id, is_valid in result.items():
                        cache_key = cache_item_key_map.get(item_id)
                        if cache_key:
                            self.cache.set(cache_key, is_valid)

        return valid_data
