import asyncio
import logging

import hashlib

from diskcache import Cache, JSONDisk


from llm.base import BaseLLMProvider
from config.config import ValidationSettings

from models.adjudicator import EntitiesAdjudicator

from prompts.adjudicator import ADJUDICATOR_ENTITY_PROMPT, ADJUDICATOR_CONTEXT

logger = logging.getLogger(__name__)


class EntityAdjudicator:

    def __init__(self, llm_provider: "BaseLLMProvider", config: ValidationSettings):
        self.llm = llm_provider
        self.config = config
        self.semaphore = asyncio.Semaphore(self.config.max_concurrent)
        cache_dir = self.config.cached_dir
        self.cache = Cache(
            directory=cache_dir, disk=JSONDisk, eviction_policy="least-recently-used"
        )

    def _generate_cache_key(self, prompt: str, content: str) -> str:
        return hashlib.sha256(f"{prompt}:{content}".encode()).hexdigest()

    async def _llm_adjudicate(self, content: str) -> EntitiesAdjudicator | None:
        for attempt in range(self.config.max_retries):
            try:
                async with self.semaphore:
                    return await self.llm.generate_json(
                        prompt=ADJUDICATOR_ENTITY_PROMPT,
                        temperature=0.0,
                        contents=content,
                        response_schema=EntitiesAdjudicator,
                    )
            except Exception as e:
                last_attempt = attempt == self.config.max_retries - 1
                if last_attempt:
                    logger.error(f"Entity adjudicate failed: {e}")
                    return None

                wait_time = self.config.base_wait_sec ** (attempt + 1)
                logger.warning(
                    f"Retry LLM API calling {attempt + 1}/{self.config.max_retries}"
                )
                await asyncio.sleep(wait_time)
        return None

    async def adjudicate(
        self,
        entity_a_name: str,
        entity_a_context: str,
        entity_b_name: str,
        entity_b_context: str,
    ) -> EntitiesAdjudicator | None:

        entities = [
            {"name": entity_a_name, "context": entity_a_context},
            {"name": entity_b_name, "context": entity_b_context},
        ]
        entities.sort(key=lambda x: x["name"].lower())

        content = ADJUDICATOR_CONTEXT.format(
            entity_a_name=entities[0]["name"],
            entity_a_context=entities[0]["context"],
            entity_b_name=entities[1]["name"],
            entity_b_context=entities[1]["context"],
        )

        cache_key = self._generate_cache_key(ADJUDICATOR_ENTITY_PROMPT, content)
        cache_result = self.cache.get(cache_key)

        if cache_result is not None:
            return EntitiesAdjudicator(**cache_result)

        logger.info(f"Cache miss for key: {cache_key}. Calling LLM for adjudication.")
        result = await self._llm_adjudicate(content)
        if result is not None:
            model_result = (
                EntitiesAdjudicator(**result) if isinstance(result, dict) else result
            )
            self.cache.set(cache_key, result)
            return model_result

        return None
