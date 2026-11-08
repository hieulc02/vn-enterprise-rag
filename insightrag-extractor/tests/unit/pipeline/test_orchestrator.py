import pytest


from unittest.mock import AsyncMock, ANY, MagicMock, call, patch

from app.models.broker import Message, Payload, DocumentEventType
from app.pipeline.orchestrator import DocumentOrchestrator


@pytest.fixture
def orchestrator():
    return DocumentOrchestrator(
        notification_topic="test-topic",
        ingestion=AsyncMock(),
        resolution=AsyncMock(),
        storage=AsyncMock(),
        producer=AsyncMock(),
    )


@pytest.fixture
def message():
    return Message(
        id="test-id",
        aggregate_type="DOCUMENT",
        event_type=DocumentEventType.DOCUMENT_CREATED,
        payload=Payload(
            file_key="test-file.pdf",
            bucket_name="bucket",
            content_type="application/pdf",
        ),
    )


@pytest.fixture
def graph():
    mock_graph = MagicMock()
    mock_graph.model_dump.return_value = {"nodes": [], "edges": []}
    return mock_graph


class TestDocumentOrchestrator:

    @pytest.mark.asyncio
    async def test_process_document_on_creation(self, orchestrator, message, graph):

        orchestrator.storage.exists.side_effect = [True, False, True]

        orchestrator.ingestion.ingest.return_value = (["chunk_graph"], ["chunk"])
        orchestrator.resolution.resolve_entities.return_value = graph
        result = await orchestrator.process_document(message, message_key="test-key")

        assert result is True

        orchestrator.storage.download_to_file.assert_called_once()
        orchestrator.ingestion.ingest.assert_called_once()
        orchestrator.resolution.resolve_entities.assert_called_once()
        orchestrator.storage.save_file.assert_called_once_with(
            "bucket", ANY, {"nodes": [], "edges": []}
        )
        orchestrator.producer.send_and_wait.assert_called_once_with(
            topic="test-topic", value=ANY, key=b"test-file.pdf"
        )

    @pytest.mark.asyncio
    async def test_process_document_on_deletion(self, orchestrator, message):
        message.event_type = DocumentEventType.DOCUMENT_DELETED

        orchestrator.delete_all_related_files = AsyncMock()

        result = await orchestrator.process_document(message, message_key="test-key")

        assert result is True

        orchestrator.delete_all_related_files.assert_called_once_with(
            file_key="test-file.pdf", message_key="test-key", bucket="bucket"
        )
        orchestrator.producer.send_and_wait.assert_called_once_with(
            topic="test-topic", value=ANY, key=b"test-file.pdf"
        )

    @pytest.mark.asyncio
    async def test_process_document_on_unsupported_event_type(
        self, orchestrator, message
    ):
        message.event_type = DocumentEventType.DOCUMENT_COMPLETED
        orchestrator.handle_creation = AsyncMock()
        orchestrator.handle_deletion = AsyncMock()

        result = await orchestrator.process_document(message, message_key="test-key")

        assert result is True

        orchestrator.handle_creation.assert_not_called()
        orchestrator.handle_deletion.assert_not_called()

    @pytest.mark.asyncio
    async def test_handle_creation_aborts_if_source_file_is_missing(
        self, orchestrator, message
    ):
        orchestrator.storage.exists.return_false = False

        result = await orchestrator.handle_creation(message, message_key="test-key")

        assert result is True

        orchestrator.storage.download_to_file.assert_not_called()
        orchestrator.ingestion.ingest.assert_not_called()

    @pytest.mark.asyncio
    async def test_handle_creation_skips_if_graph_already_exists(
        self, orchestrator, message
    ):
        orchestrator.storage.exists.side_effect = [False, True]

        result = await orchestrator.handle_creation(message, message_key="test-key")

        assert result is True

        orchestrator.storage.download_to_file.assert_not_called()
        orchestrator.ingestion.ingest.assert_not_called()

    @pytest.mark.asyncio
    async def test_handle_creation_aborts_if_file_deleted_during_download(
        self, orchestrator, message
    ):
        orchestrator.storage.exists.side_effect = [True, False, False]

        result = await orchestrator.handle_creation(message, message_key="test-key")

        assert result is True

        orchestrator.storage.download_to_file.assert_called_once()
        orchestrator.ingestion.ingest.assert_not_called()

    @pytest.fixture
    def mock_file_path(self):
        path_mock = MagicMock()
        path_mock.unlink = MagicMock()
        return path_mock

    @pytest.fixture
    def path_extract(self, mock_file_path):
        with patch("app.pipeline.orchestrator._extract_file_data") as mock_extract:
            mock_extract.return_value = ("bucket", "test-file.pdf", mock_file_path)
            yield mock_extract

    @pytest.mark.asyncio
    async def test_handle_creation_on_update(
        self, orchestrator, message, graph, mock_file_path, path_extract
    ):
        orchestrator.storage.exists.side_effect = [True, False, True]

        orchestrator.ingestion.ingest.return_value = (["chunk_graph"], ["chunk"])
        orchestrator.resolution.resolve_entities.return_value = graph

        result = await orchestrator.handle_creation(
            message, message_key="test-key", is_update=True
        )

        assert result is True

        orchestrator.ingestion.ingest.assert_called_once_with(
            file_path=mock_file_path,
            bucket="bucket",
            file_key="test-file.pdf",
            message_key="test-key",
            is_update=True,
        )

        orchestrator.storage.save_file.assert_called_once_with(
            "bucket", ANY, graph.model_dump()
        )

        orchestrator.producer.send_and_wait.assert_called_once()
        mock_file_path.unlink.assert_called_once_with(missing_ok=True)

    @pytest.mark.asyncio
    async def test_handle_creation_caches_exceptions(
        self, orchestrator, message, mock_file_path, path_extract
    ):
        orchestrator.storage.exists.side_effect = [True, False, True]

        orchestrator.ingestion.ingest.side_effect = Exception("LLM timeout")

        result = await orchestrator.handle_creation(
            message, message_key="test-key", is_update=False
        )

        assert result is False
        orchestrator.storage.save_file.assert_not_called()
        orchestrator.producer.send_and_wait.assert_not_called()

        mock_file_path.unlink.assert_called_once_with(missing_ok=True)

    @pytest.mark.asyncio
    async def test_handle_deletion_success(self, orchestrator, message, path_extract):
        orchestrator.delete_all_related_files = AsyncMock()

        result = await orchestrator.handle_deletion(message, message_key="test-key")

        assert result is True
        orchestrator.delete_all_related_files.assert_called_once_with(
            file_key="test-file.pdf", message_key="test-key", bucket="bucket"
        )

        orchestrator.producer.send_and_wait.assert_called_once_with(
            topic="test-topic",
            value=ANY,
            key="test-file.pdf".encode("utf-8"),
        )

    @pytest.mark.asyncio
    async def test_handle_deletion_catches_exceptions(self, orchestrator, message):
        orchestrator.delete_all_related_files = AsyncMock(
            side_effect=Exception("Storage offline")
        )

        result = await orchestrator.handle_deletion(message, message_key="test-key")

        assert result is False
        orchestrator.producer.send_and_wait.assert_not_called()

    @pytest.mark.asyncio
    async def test_delete_all_related_files_success(self, orchestrator):
        await orchestrator.delete_all_related_files(
            file_key="test-file.pdf", message_key="message-id", bucket="bucket"
        )

        assert orchestrator.storage.delete_target.call_count == 3

        expected_calls = [
            call("bucket", "message-id/intermediate/chunks/test-file.json"),
            call("bucket", "message-id/intermediate/extractions/"),
            call("bucket", "message-id/output/graph_test-file.json"),
        ]

        orchestrator.storage.delete_target.assert_has_calls(
            expected_calls, any_order=True
        )

    @pytest.mark.asyncio
    async def test_delete_all_related_files_continues_on_partial_error(
        self, orchestrator
    ):
        orchestrator.storage.delete_target.side_effect = [
            False,
            Exception("File locked"),
            False,
        ]

        await orchestrator.delete_all_related_files(
            bucket="bucket", message_key="message-id", file_key="test-file.pdf"
        )

        assert orchestrator.storage.delete_target.call_count == 3


import tempfile
from pathlib import Path

from app.pipeline.orchestrator import (
    _build_tmp_path,
    _extract_file_data,
    _build_graph_filename,
    _build_response_message,
)


class TestOrchestratorHelper:

    @pytest.mark.parametrize(
        "file_key, content_type, expected_suffix",
        [
            ("docs/report.pdf", "application/pdf", ".pdf"),
            ("docs/weird_file", "application/unknown", ".bin"),
            ("docs/image.jpeg", None, ".jpeg"),
            ("docs/no_extension", None, ""),
        ],
    )
    def test_build_tmp_path(self, file_key, content_type, expected_suffix):
        result_path = _build_tmp_path(file_key, content_type)
        assert result_path.parent == Path(tempfile.gettempdir())

        assert result_path.name.startswith(Path(file_key).stem)
        assert result_path.suffix == expected_suffix

        expected_stem_length = len(Path(file_key).stem) + 1 + 8
        assert len(result_path.stem) == expected_stem_length

    @pytest.mark.parametrize(
        "message_key, file_key, expected",
        [
            ("id-1", "docs/report.pdf", "id-1/output/graph_report.json"),
            ("id-2", "test.txt", "id-2/output/graph_test.json"),
            ("id-3", "no_extension", "id-3/output/graph_no_extension.json"),
        ],
    )
    def test_build_graph_filename(self, message_key, file_key, expected):
        assert _build_graph_filename(message_key, file_key) == expected

    def test_extract_file_data(self):
        mock_message = MagicMock()
        mock_message.payload.bucket_name = "test-bucket"
        mock_message.payload.file_key = "docs/report.pdf"
        mock_message.payload.content_type = "application/pdf"

        bucket, file_key, file_path = _extract_file_data(mock_message)

        assert bucket == "test-bucket"
        assert file_key == "docs/report.pdf"
        assert isinstance(file_path, Path)
        assert file_path.suffix == ".pdf"

    def test_build_response_message(self):
        response = _build_response_message(
            correlation_id="corr-123",
            file_key="graph_report.json",
            bucket="test-bucket",
            event_type=DocumentEventType.DOCUMENT_COMPLETED,
        )

        assert response.correlation_id == "corr-123"
        assert response.event_type == DocumentEventType.DOCUMENT_COMPLETED
        assert response.aggregate_type == "DOCUMENT"

        assert response.payload.file_key == "graph_report.json"
        assert response.payload.bucket_name == "test-bucket"
        assert response.payload.content_type == "application/json"
