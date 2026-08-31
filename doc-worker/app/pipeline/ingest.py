import asyncio
import logging
import tempfile
from pathlib import Path

from pydantic import TypeAdapter

from pipeline.registry import ParserRegistry
from pipeline.router import document_page_classify

from pipeline.tree_builder import build_document_tree
from chunking.sematic import SemanticChunker

from models.domain import ParsedBlock
from models.chunk import DocumentChunk

from extraction.schema import SchemaExtractor
from storage.base import StorageBase

from embedding.transformer import Embedder
from utils.files import build_filename_with_prefix_folder

logger = logging.getLogger(__name__)


class IngestionPipeline:

    def __init__(
        self,
        registry: ParserRegistry,
        chunker: SemanticChunker,
        extracter: SchemaExtractor,
        embedder: Embedder,
        storage: StorageBase,
    ):
        self.registry = registry
        self.chunker = chunker
        self.extracter = extracter
        self.embedder = embedder
        self.storage = storage

    async def ingest(self, file_path: str, bucket: str, file_key: str):

        chunk_cache_key = build_filename_with_prefix_folder("chunks", file_key)

        if await self.storage.exists(bucket, chunk_cache_key):
            logger.info(f"Loading chunks from cache for file key: {file_key}")

            try:
                raw_chunks = await self.storage.load_file(bucket, chunk_cache_key)
                chunk_adapter = TypeAdapter(list[DocumentChunk])
                chunks = chunk_adapter.validate_python(raw_chunks)
            except Exception as e:
                logger.error(f"Failed to load from cache. Re-process document: {e}")
                chunks = await self._chunk_document(
                    file_path, bucket, file_key, chunk_cache_key
                )
        else:
            chunks = await self._chunk_document(
                file_path, bucket, file_key, chunk_cache_key
            )

        extracted_chunks = await self.extracter.extract_all(bucket, chunks)

        doc_extraction_key = build_filename_with_prefix_folder("extractions", file_key)
        if not await self.storage.exists(bucket, doc_extraction_key):
            await self.storage.save_file(
                bucket,
                doc_extraction_key,
                [extraction.model_dump(mode="json") for extraction in extracted_chunks],
            )

        return extracted_chunks, chunks

    async def _chunk_document(
        self, file_path: str, bucket: str, file_key: str, chunk_cache_key: str
    ) -> list[DocumentChunk]:
        parsed_blocks = await self._parse_document(file_path=file_path)

        node = await asyncio.to_thread(build_document_tree, parsed_blocks)
        chunks: list[DocumentChunk] = await asyncio.to_thread(
            self.chunker.chunk, node, file_key
        )

        await self._embed_document(chunks)
        await self.storage.save_file(
            bucket, chunk_cache_key, [c.model_dump(mode="json") for c in chunks]
        )

        return chunks

    async def _embed_document(self, chunks: list[DocumentChunk]):
        embeddings = await self.embedder.aembed([chunk.text for chunk in chunks])

        for chunk, embedding in zip(chunks, embeddings):
            chunk.embedding = embedding.tolist()

    async def _parse_document(self, file_path: str, **kwargs):
        classified_docs = await asyncio.to_thread(document_page_classify, file_path)

        tasks = []
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_dir_path = Path(temp_dir)

            for doc_type, (sub_doc, page_map) in classified_docs.items():
                try:
                    if len(sub_doc) == 0:
                        continue

                    if not self.registry.has_type(doc_type):
                        raise ValueError(f"No pipeline is register for {doc_type}")

                    temp_file_path = temp_dir_path / f"sub_{doc_type}.pdf"
                    await asyncio.to_thread(sub_doc.save, temp_file_path)

                    parser, handler = self.registry.get_pipeline(doc_type)

                    parser_task = asyncio.create_task(parser.parse(temp_file_path))
                    tasks.append((doc_type, page_map, handler, parser_task))
                finally:
                    sub_doc.close()

            tasks_only = [t[3] for t in tasks]
            gathered_results = await asyncio.gather(*tasks_only, return_exceptions=True)

        handler_tasks = []
        handler_meta = []

        for task, result in zip(tasks, gathered_results):
            doc_type, page_map, handler, _ = task

            if isinstance(result, Exception):
                logger.error(f"Parser for {doc_type} failed: {result}")
                continue

            h_task = asyncio.create_task(handler(result, page_map))
            handler_tasks.append(h_task)
            handler_meta.append(doc_type)

        parsed_blocks: list[ParsedBlock] = []

        if handler_tasks:
            handler_results = await asyncio.gather(
                *handler_tasks, return_exceptions=True
            )
            for doc_type, result in zip(handler_meta, handler_results):
                if isinstance(result, Exception):
                    logger.error(f"Handler for {doc_type} failed: {result}")
                    continue

                parsed_blocks.extend(result)

        parsed_blocks.sort(key=lambda x: (x.original_page, x.page_order))
        return parsed_blocks
