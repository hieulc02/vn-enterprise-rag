import asyncio
import pytest


from unittest.mock import AsyncMock, MagicMock, patch

from app.models.extraction import ChunkExtraction, ChunkGraph
from app.models.chunk import DocumentChunk, ChunkMetadata
from app.extraction.schema import SchemaExtractor
from app.config.config import ExtractionSettings


@pytest.fixture
def mock_config():
    config = ExtractionSettings()
    config.max_concurrent = 2
    config.version = "v1.1"
    config.max_retries = 3
    config.base_wait_sec = 1
    return config


@pytest.fixture
def mock_storage():
    return AsyncMock()


@pytest.fixture
def extractor(mock_config, mock_storage, mock_llm):
    return SchemaExtractor(
        llm_provider=mock_llm, storage=mock_storage, config=mock_config
    )


@pytest.fixture
def dummy_extraction():
    extraction = MagicMock(spec=ChunkExtraction)
    extraction.model_dump.return_value = {
        "contains_narrative_text": False,
        "contains_table": False,
        "entities": [],
        "relationships": [],
    }
    return extraction


@pytest.fixture
def dummy_chunk():
    chunk = MagicMock(spec=DocumentChunk)
    chunk.chunk_id = "chunk-id"
    chunk.text = "chunk-text"
    chunk.metadata = ChunkMetadata(document_key="test-document.pdf", page_number=1)
    return chunk


class TestSchemaExtractor:

    def test_get_cache_key(self, extractor):
        assert (
            extractor._get_cache_key("message-id", "chunk-id")
            == "message-id/intermediate/extractions/v1.1/chunk-id.json"
        )

    @pytest.mark.asyncio
    async def test_extract_chunk_cache_hit(
        self, extractor, mock_storage, dummy_extraction, dummy_chunk, mock_llm
    ):
        mock_storage.exists.return_value = True
        mock_storage.load_file.return_value = dummy_extraction

        with patch("app.extraction.schema.ChunkGraph.model_validate") as mock_validate:
            mock_validate.return_value = MagicMock(spec=ChunkGraph)
            result = await extractor._extract_chunk("bucket", "message-id", dummy_chunk)

            assert result is not None

            mock_storage.load_file.assert_called_once()
            mock_llm.generate_json.assert_not_called()
            mock_storage.save_file.assert_not_called()

    @pytest.mark.asyncio
    async def test_extract_chunk_corrupted_cache_fallback(
        self, extractor, mock_storage, dummy_extraction, dummy_chunk, mock_llm
    ):
        mock_storage.exists.return_value = True
        mock_storage.load_file.side_effect = Exception("Malformed Json")
        mock_llm.generate_json.return_value = dummy_extraction

        result = await extractor._extract_chunk("bucket", "message-id", dummy_chunk)

        assert result is not None
        mock_llm.generate_json.assert_called_once()

    @pytest.mark.asyncio
    async def test_extract_chunk_cache_miss_and_save(
        self, extractor, mock_storage, dummy_extraction, dummy_chunk, mock_llm
    ):
        mock_storage.exists.return_value = False
        mock_llm.generate_json.return_value = dummy_extraction

        result = await extractor._extract_chunk("bucket", "message-id", dummy_chunk)

        assert result is not None
        mock_llm.generate_json.assert_called_once()
        mock_storage.save_file.assert_called_once()

    @pytest.mark.asyncio
    async def test_call_llm_with_retry_success_first_try(
        self, extractor, dummy_extraction, dummy_chunk, mock_llm
    ):
        mock_llm.generate_json.return_value = dummy_extraction

        with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep:
            result = await extractor._call_llm_with_retry(dummy_chunk)
            assert result is not None
            mock_llm.generate_json.assert_called_once()
            mock_sleep.assert_not_called()

    @pytest.mark.asyncio
    async def test_call_llm_with_retry_failure_then_success(
        self, extractor, dummy_extraction, dummy_chunk, mock_llm
    ):
        mock_llm.generate_json.side_effect = [
            Exception("Service unavailable"),
            dummy_extraction,
        ]

        with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep:
            result = await extractor._call_llm_with_retry(dummy_chunk)
            assert result is not None
            assert mock_llm.generate_json.call_count == 2
            mock_sleep.assert_called_once()

    @pytest.mark.asyncio
    async def test_call_llm_with_retry_exhaustion_of_retries(
        self, extractor, dummy_chunk, mock_llm
    ):
        mock_llm.generate_json.return_value = Exception("Service unavailable")

        with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep:
            result = await extractor._call_llm_with_retry(dummy_chunk)

            assert result is None
            assert mock_llm.generate_json.call_count == extractor.config.max_retries
            assert mock_sleep.call_count == extractor.config.max_retries - 1

    @pytest.mark.asyncio
    async def test_call_llm_with_retry_semaphore_limits_concurrency(
        self, extractor, mock_llm
    ):
        current_task = 0
        max_task = 0

        async def mock_generate_json_delayed(*arg, **kwargs):
            nonlocal current_task, max_task

            current_task += 1
            max_task = max(max_task, current_task)
            await asyncio.sleep(0.01)
            current_task -= 1

            result = MagicMock(spec=ChunkExtraction)
            result.model_dump.return_value = {
                "contains_narrative_text": False,
                "contains_table": False,
                "entities": [],
                "relationships": [],
            }
            return result

        mock_llm.generate_json.side_effect = mock_generate_json_delayed

        chunks = [
            MagicMock(spec=DocumentChunk, chunk_id=f"chunk-id{i}", text="data")
            for i in range(5)
        ]

        tasks = [
            asyncio.create_task(extractor._call_llm_with_retry(chunk))
            for chunk in chunks
        ]
        await asyncio.gather(*tasks)

        assert mock_llm.generate_json.call_count == 5
        assert max_task == extractor.config.max_concurrent

    @pytest.mark.asyncio
    async def test_extract_all_handles_partial_failure(self, extractor, mock_llm):
        chunk_good = MagicMock(spec=DocumentChunk)
        chunk_bad = MagicMock(spec=DocumentChunk)

        mock_graph = MagicMock(spec=ChunkGraph)

        with patch.object(
            extractor,
            "_extract_chunk",
            side_effect=[mock_graph, Exception("Unexpected runtime error")],
        ) as mock_extract:
            results = await extractor.extract_all(
                "bucket", "message-id", [chunk_good, chunk_bad]
            )

            assert len(results) == 1
            assert results[0] == mock_graph
