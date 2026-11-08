import pytest

from unittest.mock import MagicMock, AsyncMock, ANY, patch

from app.pipeline.ingest import IngestionPipeline
from app.models.chunk import DocumentChunk
from app.models.extraction import ChunkExtraction
from app.models.domain import ParsedBlock


@pytest.fixture
def mock_registry():
    return MagicMock()


@pytest.fixture
def mock_chunker():
    return MagicMock()


@pytest.fixture
def mock_extracter():
    extractor = AsyncMock()
    extractor.extract_all.return_value = [MagicMock(spec=ChunkExtraction)]
    return extractor


@pytest.fixture
def mock_embedder():
    return AsyncMock()


@pytest.fixture
def mock_storage():
    return AsyncMock()


@pytest.fixture
def pipeline(mock_registry, mock_chunker, mock_extracter, mock_embedder, mock_storage):
    return IngestionPipeline(
        registry=mock_registry,
        chunker=mock_chunker,
        extracter=mock_extracter,
        embedder=mock_embedder,
        storage=mock_storage,
    )


@pytest.fixture
def dummy_chunk():
    chunk = MagicMock(spec=DocumentChunk)
    chunk.model_dump.return_value = {"chunk_id": "1", "text": "test-content"}
    return chunk


class TestIngestionPipeline:

    @pytest.mark.asyncio
    async def test_ingest_cache_hit_skips_chunking(
        self, pipeline, mock_storage, dummy_chunk
    ):
        mock_storage.exists.return_value = True
        mock_storage.load_file.return_value = [
            {"chunk_id": "1", "text": "test-content"}
        ]

        with patch("app.pipeline.ingest.TypeAdapter") as mock_adapter, patch.object(
            pipeline, "_chunk_document", new_callable=AsyncMock
        ) as mock_chunk_doc:
            mock_adapter.return_value.validate_python.return_value = [dummy_chunk]

            extracted_chunks, chunks = await pipeline.ingest(
                file_path="tmp/dummy.pdf",
                bucket="bucket",
                message_key="message-id",
                file_key="dummy.pdf",
            )

            assert len(chunks) == 1
            mock_chunk_doc.assert_not_called()
            mock_storage.load_file.assert_called_once()
            pipeline.extracter.extract_all.assert_called_once_with(
                "bucket", "message-id", [dummy_chunk]
            )

    @pytest.mark.asyncio
    async def test_ingest_cache_corrupted_fallback(
        self, pipeline, mock_storage, dummy_chunk
    ):
        mock_storage.exists.return_value = True
        mock_storage.load_file.side_effect = Exception("Corrupted JSON")

        with patch.object(
            pipeline, "_chunk_document", new_callable=AsyncMock
        ) as mock_chunk_doc:
            mock_chunk_doc.return_value = [dummy_chunk]
            await pipeline.ingest(
                file_path="tmp/dummy.pdf",
                bucket="bucket",
                message_key="message-id",
                file_key="dummy.pdf",
            )
            mock_chunk_doc.assert_called_once()

    @pytest.mark.asyncio
    async def test_ingest_is_update_bypasses_cache(self, pipeline, dummy_chunk):
        with patch.object(
            pipeline, "_chunk_document", new_callable=AsyncMock
        ) as mock_chunk_doc:
            mock_chunk_doc.return_value = [dummy_chunk]
            await pipeline.ingest(
                file_path="tmp/dummy.pdf",
                bucket="bucket",
                file_key="dummy.pdf",
                message_key="message-id",
                is_update=True,
            )
            mock_chunk_doc.assert_called_once()

    @pytest.mark.asyncio
    @patch("app.pipeline.ingest.build_document_tree")
    async def test_chunk_document_flow(
        self, mock_build_tree, pipeline, mock_chunker, dummy_chunk, mock_storage
    ):
        mock_build_tree.return_value = MagicMock()
        mock_chunker.chunk.return_value = [dummy_chunk]

        with patch.object(
            pipeline, "_parse_document", new_callable=AsyncMock
        ) as mock_parse, patch.object(
            pipeline, "_embed_document", new_callable=AsyncMock
        ) as mock_embed:

            mock_parse.return_value = [MagicMock(spec=ParsedBlock)]
            result = await pipeline._chunk_document(
                file_path="tmp/dummy.pdf",
                bucket="bucket",
                file_key="dummy.pdf",
                chunk_cache_key="chunk/dummy.pdf",
            )

            assert result == [dummy_chunk]
            mock_parse.assert_called_once_with(file_path="tmp/dummy.pdf")
            mock_build_tree.assert_called_once()
            mock_chunker.chunk.assert_called_once()
            mock_embed.assert_called_once_with([dummy_chunk])
            mock_storage.save_file.assert_called_once()

    @pytest.mark.asyncio
    async def test_embed_document_mutates_chunk(self, pipeline, mock_embedder):
        chunk1 = MagicMock(spec=DocumentChunk, text="chunk 1")
        chunk2 = MagicMock(spec=DocumentChunk, text="chunk 2")
        chunks = [chunk1, chunk2]

        vec1, vec2 = MagicMock(), MagicMock()
        vec1.tolist.return_value = [0.1, 0.2]
        vec2.tolist.return_value = [0.3, 0.4]

        mock_embedder.aembed.return_value = [vec1, vec2]

        await pipeline._embed_document(chunks)

        mock_embedder.aembed.assert_called_once_with(["chunk 1", "chunk 2"])
        assert chunk1.embedding == [0.1, 0.2]
        assert chunk2.embedding == [0.3, 0.4]

    @pytest.mark.asyncio
    @patch("app.pipeline.ingest.document_page_classify")
    async def test_parse_document_on_partial_failure(
        self, mock_classify, pipeline, mock_registry
    ):
        mock_sub_doc = MagicMock()

        mock_sub_doc.__len__.return_value = 1
        mock_classify.return_value = {
            "digital": (mock_sub_doc, {1: 1}),
            "scanned": (mock_sub_doc, {2: 2}),
        }

        mock_registry.has_type.return_value = True

        digital_parser = AsyncMock()

        digital_handler = AsyncMock()
        digital_block = MagicMock(spec=ParsedBlock)
        digital_block.original_page = 1
        digital_block.page_order = 1
        digital_handler.return_value = [digital_block]

        scanned_parser = AsyncMock()
        scanned_parser.parse.side_effect = Exception("Parser service is outage")
        scanned_handler = AsyncMock()

        def mock_get_pipeline(doc_type):
            if doc_type == "digital":
                return digital_parser, digital_handler
            return scanned_parser, scanned_handler

        mock_registry.get_pipeline.side_effect = mock_get_pipeline

        blocks = await pipeline._parse_document(file_path="tmp/dummy.pdf")
        assert len(blocks) == 1
        assert blocks[0] == digital_block

        digital_parser.parse.assert_called_once()
        scanned_parser.parse.assert_called_once()

        digital_handler.assert_called_once_with(ANY, {1: 1})
        scanned_handler.assert_not_called()
