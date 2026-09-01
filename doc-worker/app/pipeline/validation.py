import asyncio
import logging
from typing import Any

import orjson as json

from llm.base import BaseLLMProvider
from config.config import ValidationSettings

logger = logging.getLogger(__name__)


class DocumentValidation:

    def __init__(self, llm_provider: "BaseLLMProvider", config: ValidationSettings):
        self.llm = llm_provider
        self.config = config
        self.semaphore = asyncio.Semaphore(self.config.max_concurrent)

    async def _validate_chunk(self, prompt: str, chunk: str) -> dict:
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

        tasks = [self._validate_chunk(prompt, chunk) for chunk in validate_data]
        results = await asyncio.gather(*tasks)

        valid_data = {}

        for result in results:
            if isinstance(result, dict):
                valid_data.update(result)

        return valid_data
