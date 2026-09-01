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


def build_registry_pipeline(llm: BaseLLMProvider, settings: AppSettings):
    registry = ParserRegistry()

    doc_validator = DocumentValidation(llm, settings.validation)

    from parsers.docling import DoclingParser
    from parsers.docling_handler import docling_handler

    registry.register(
        doc_type="digital",
        parser=DoclingParser(use_ocr=False),
        handler=docling_handler(doc_validator),
    )

    from parsers.llama import LLamaParser
    from parsers.llama_handler import llama_handler

    registry.register(
        doc_type="scanned",
        parser=LLamaParser(settings.llama.LLAMA_CLOUD_API_KEY),
        handler=llama_handler(),
    )

    return registry


def build_ingestion_pipeline(
    settings: AppSettings,
    storage: StorageBase,
    llm: BaseLLMProvider,
    embedder: Embedder,
    registry: ParserRegistry,
):
    chunker_config = ChunkerConfig.from_llm(
        llm_provider=llm, target_chunk_size=1200, overlap=100
    )

    from chunking.sematic import SemanticChunker

    tokenizer = get_tokenizer(settings=settings)
    chunker = SemanticChunker(
        config=chunker_config, token_function=tokenizer.num_tokens
    )

    from extraction.schema import SchemaExtractor

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
