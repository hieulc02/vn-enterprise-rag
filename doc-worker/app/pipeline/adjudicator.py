import asyncio
import logging


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

    async def adjudicate(
        self,
        entity_a_name: str,
        entity_a_context: str,
        entity_b_name: str,
        entity_b_context: str,
    ) -> EntitiesAdjudicator | None:
        content = ADJUDICATOR_CONTEXT.format(
            entity_a_name=entity_a_name,
            entity_a_context=entity_a_context,
            entity_b_name=entity_b_name,
            entity_b_context=entity_b_context,
        )
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
