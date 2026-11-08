import pytest

from unittest.mock import MagicMock, patch
from app.pipeline.container import (
    build_registry_pipeline,
    build_ingestion_pipeline,
    build_merge_pipeline,
)


@pytest.fixture
def mock_llm():
    return MagicMock()


@pytest.fixture
def mock_storage():
    return MagicMock()


@pytest.fixture
def mock_embedder():
    return MagicMock()


@pytest.fixture
def mock_settings():
    settings = MagicMock()

    settings.validation = MagicMock()
    settings.docling.use_ocr = False
    settings.llama.LLAMA_CLOUD_API_KEY = "mock-llama-key"

    settings.chunking.target_chunk_size = 1200
    settings.chunking.overlap = 100

    settings.extraction = MagicMock()
    return settings


class TestBuildRegistryPipeline:

    @patch("app.pipeline.container.DocumentValidation")
    @patch("app.pipeline.container.DoclingParser")
    @patch("app.pipeline.container.docling_handler")
    @patch("app.pipeline.container.LLamaParser")
    @patch("app.pipeline.container.llama_handler")
    def test_build_registry_pipeline(
        self,
        mock_llama_handler,
        mock_llama_parser,
        mock_docling_handler,
        mock_docling_parser,
        mock_validator,
        mock_llm,
        mock_settings,
    ):
        registry = build_registry_pipeline(llm=mock_llm, settings=mock_settings)

        mock_validator.assert_called_once_with(mock_llm, mock_settings.validation)

        mock_docling_parser.assert_called_once_with(use_ocr=False)
        mock_llama_parser.assert_called_once_with("mock-llama-key")

        assert "digital" in registry._parsers
        assert "scanned" in registry._parsers

    @patch("app.pipeline.container.DocumentValidation")
    @patch("app.pipeline.container.DoclingParser")
    @patch("app.pipeline.container.LLamaParser")
    def test_build_registry_pipeline_missing_llama_key(
        self,
        mock_llama_parser,
        mock_docling_parser,
        mock_validator,
        mock_llm,
        mock_settings,
        caplog,
    ):
        mock_settings.llama = None
        registry = build_registry_pipeline(llm=mock_llm, settings=mock_settings)
        mock_validator.assert_called_once_with(mock_llm, mock_settings.validation)
        mock_docling_parser.assert_called_once()
        mock_llama_parser.assert_not_called()

        assert "Scanned document support is disable" in caplog.text


# build_ingestion_pipeline
class TestBuildIngestionPipeline:

    @patch("app.pipeline.container.ChunkerConfig")
    @patch("app.pipeline.container.get_tokenizer")
    @patch("app.pipeline.container.SemanticChunker")
    @patch("app.pipeline.container.SchemaExtractor")
    @patch("app.pipeline.container.IngestionPipeline")
    def test_build_ingestion_pipeline_wiring(
        self,
        mock_ingestion_pipeline,
        mock_schema_extractor,
        mock_semantic_chunker,
        mock_get_tokenizer,
        mock_chunker_config,
        mock_settings,
        mock_storage,
        mock_llm,
        mock_embedder,
    ):
        mock_registry = MagicMock()
        mock_tokenizer_instance = MagicMock()
        mock_get_tokenizer.return_value = mock_tokenizer_instance

        pipeline = build_ingestion_pipeline(
            mock_settings, mock_storage, mock_llm, mock_embedder, mock_registry
        )

        mock_chunker_config.from_llm.assert_called_once_with(
            llm_provider=mock_llm, target_chunk_size=1200, overlap=100
        )

        mock_schema_extractor.assert_called_once_with(
            llm_provider=mock_llm, storage=mock_storage, config=mock_settings.extraction
        )

        mock_ingestion_pipeline.assert_called_once_with(
            registry=mock_registry,
            chunker=mock_semantic_chunker.return_value,
            extracter=mock_schema_extractor.return_value,
            embedder=mock_embedder,
            storage=mock_storage,
        )


class TestBuildMergePipeline:

    @patch("app.pipeline.container.EntityRegistry")
    @patch("app.pipeline.container.EntityAdjudicator")
    @patch("app.pipeline.container.EntityResolutionPipeline")
    def test_build_merge_pipeline_wiring(
        self,
        mock_resolution_pipeline,
        mock_adjudicator,
        mock_entity_registry,
        mock_settings,
        mock_llm,
        mock_embedder,
    ):
        pipeline = build_merge_pipeline(
            settings=mock_settings, llm=mock_llm, embedder=mock_embedder
        )

        mock_adjudicator.assert_called_once_with(mock_llm, mock_settings.validation)

        mock_resolution_pipeline.assert_called_once_with(
            registry=mock_entity_registry.return_value,
            embedder=mock_embedder,
            adjudicator=mock_adjudicator.return_value,
        )
