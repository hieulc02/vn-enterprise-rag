import asyncio
import logging

import orjson as json

from llm.base import BaseLLMProvider
from config.config import ExtractionSettings

from prompts.extract_graph import EXTRACT_GRAPH_PROMPT

from models.extraction import ChunkExtraction, ChunkGraph
from models.chunk import DocumentChunk
from storage.base import StorageBase

from utils.files import build_filename_with_prefix_folder

logger = logging.getLogger(__name__)


class SchemaExtractor:

    def __init__(
        self,
        llm_provider: "BaseLLMProvider",
        storage: "StorageBase",
        config: ExtractionSettings,
    ):
        self.config = config
        self.llm = llm_provider
        self.storage = storage
        self.semaphore = asyncio.Semaphore(self.config.max_concurrent)

    def _get_cache_key(self, chunk_id: str):
        folders = f"extractions/{self.config.version}"
        return build_filename_with_prefix_folder(folders, chunk_id)

    async def _extract_chunk(
        self, bucket: str, chunk: DocumentChunk
    ) -> ChunkGraph | None:

        cache_key = self._get_cache_key(chunk.chunk_id)

        if await self.storage.exists(bucket, cache_key):
            try:
                cache_data = await self.storage.load_file(bucket, cache_key)
                if cache_data:
                    logger.debug(f"Cache HIT for chunk id: {chunk.chunk_id}")
                    return ChunkGraph.model_validate(cache_data)
            except Exception as e:
                logger.warning(
                    f"Corrupted cache for chunk {chunk.chunk_id}. Re-extractting. Error: {e}"
                )

        logger.debug(f"Cache MISS for chunk {chunk.chunk_id}. Calling LLM...")
        extraction_result = await self._call_llm_with_retry(chunk)

        if extraction_result:
            try:
                await self.storage.save_file(
                    bucket, cache_key, extraction_result.model_dump(mode="json")
                )
            except Exception as e:
                logger.error(f"Failed to write cache for chunk {chunk.chunk_id}: {e}")

        return extraction_result

    async def _call_llm_with_retry(self, chunk: DocumentChunk) -> ChunkGraph | None:

        if isinstance(chunk.text, str):
            content_str = chunk.text
        else:
            content_str = json.dumps(chunk.text).decode("utf-8")

        for attempt in range(self.config.max_retries):
            try:
                async with self.semaphore:
                    result: ChunkExtraction = await self.llm.generate_json(
                        prompt=EXTRACT_GRAPH_PROMPT,
                        temperature=0.0,
                        contents=content_str,
                        response_schema=ChunkExtraction,
                    )

                chunk_graph = None

                if result:
                    chunk_graph = ChunkGraph(
                        **result.model_dump(), chunk_id=chunk.chunk_id
                    )

                return chunk_graph

            except Exception as e:
                last_attempt = attempt == self.config.max_retries - 1
                if last_attempt:
                    logger.error(f"Document extraction failed: {e}")
                    return None

                wait_time = self.config.base_wait_sec ** (attempt + 1)
                logger.warning(
                    f"Retry LLM API calling {attempt + 1}/{self.config.max_retries}"
                )
                await asyncio.sleep(wait_time)
        return None

    async def extract_all(
        self, bucket: str, extract_chunks: list[DocumentChunk]
    ) -> list[ChunkExtraction]:
        if not extract_chunks:
            return []

        tasks = [self._extract_chunk(bucket, chunk) for chunk in extract_chunks]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        valid_results: list[ChunkGraph] = []
        for result in results:
            if isinstance(result, Exception):
                logger.error(f"Unhandled exception during extraction: {result}")
                continue
            elif isinstance(result, ChunkGraph):
                valid_results.append(result)

        failed_num = len(extract_chunks) - len(valid_results)
        if failed_num > 0:
            logger.warning(f"{failed_num} chunks failed to extract.")

        return valid_results
