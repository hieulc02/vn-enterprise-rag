import logging


from parsers.docling import DoclingParser
from parsers.docling_handler import docling_handler
from parsers.llama import LLamaParser
from parsers.llama_handler import llama_handler

from config.config import AppSettings
from config.chunker import ChunkerConfig

from llm.base import BaseLLMProvider

from pipeline.registry import ParserRegistry, EntityRegistry
from pipeline.validation import DocumentValidation
from pipeline.resolution import EntityResolutionPipeline

from pipeline.ingest import IngestionPipeline
from pipeline.adjudicator import EntityAdjudicator

from tokenizer.tokenizer_factory import get_tokenizer
from storage.base import StorageBase

from embedding.transformer import Embedder

from chunking.sematic import SemanticChunker
from extraction.schema import SchemaExtractor

logger = logging.getLogger(__name__)


def build_registry_pipeline(llm: BaseLLMProvider, settings: AppSettings):
    registry = ParserRegistry()
    doc_validator = DocumentValidation(llm, settings.validation)

    registry.register(
        doc_type="digital",
        parser=DoclingParser(use_ocr=settings.docling.use_ocr),
        handler=docling_handler(doc_validator),
    )

    llama_api_key = settings.llama.LLAMA_CLOUD_API_KEY if settings.llama else None
    if llama_api_key:
        registry.register(
            doc_type="scanned",
            parser=LLamaParser(llama_api_key),
            handler=llama_handler(),
        )
    else:
        logger.warning("Scanned document support is disable")

    return registry


def build_ingestion_pipeline(
    settings: AppSettings,
    storage: StorageBase,
    llm: BaseLLMProvider,
    embedder: Embedder,
    registry: ParserRegistry,
):
    chunker_config = ChunkerConfig.from_llm(
        llm_provider=llm,
        target_chunk_size=settings.chunking.target_chunk_size,
        overlap=settings.chunking.overlap,
    )

    tokenizer = get_tokenizer(settings=settings)
    chunker = SemanticChunker(
        config=chunker_config, token_function=tokenizer.num_tokens
    )

    extracter = SchemaExtractor(
        llm_provider=llm, storage=storage, config=settings.extraction
    )

    return IngestionPipeline(
        registry=registry,
        chunker=chunker,
        extracter=extracter,
        embedder=embedder,
        storage=storage,
    )


def build_merge_pipeline(
    settings: AppSettings, llm: BaseLLMProvider, embedder: Embedder
):
    registry = EntityRegistry()

    adjudicator = EntityAdjudicator(llm, settings.validation)
    return EntityResolutionPipeline(
        registry=registry, embedder=embedder, adjudicator=adjudicator
    )
