import logging
import tempfile
import mimetypes
import uuid
from pathlib import Path

from aiokafka import AIOKafkaProducer

from pipeline.ingest import IngestionPipeline
from pipeline.resolution import EntityResolutionPipeline

from storage.base import StorageBase

from models.broker import Payload, Message, ResponseMessage, DocumentEventType
from utils.files import build_filename_with_prefix_folder

logger = logging.getLogger(__name__)


def _build_tmp_path(file_key: str, content_type: str) -> Path:
    sys_temp_dir = Path(tempfile.gettempdir())
    if content_type:
        extension = mimetypes.guess_extension(content_type) or ".bin"
    else:
        extension = Path(file_key).suffix
    unique_id = uuid.uuid4().hex[:8]
    return sys_temp_dir / f"{Path(file_key).stem}_{unique_id}{extension}"


def _extract_file_data(message: Message):
    bucket = message.payload.bucket_name
    file_key = message.payload.file_key
    content_type = message.payload.content_type
    file_path = _build_tmp_path(file_key, content_type)
    return bucket, file_key, file_path


def _build_graph_filename(folder: str, file_key: str, extension: str = ".json") -> str:
    graph_prefix = "graph_"

    if not file_key:
        raise ValueError("File key is missing")

    file_path = Path(file_key)

    return f"{folder}/output/{graph_prefix}{file_path.stem}{extension}"


def _build_response_message(
    correlation_id: str,
    file_key: str,
    bucket: str,
    event_type: DocumentEventType,
    *,
    aggregate_type: str = "DOCUMENT",
    content_type: str = "application/json",
):
    return ResponseMessage(
        aggregate_type=aggregate_type,
        event_type=event_type,
        correlation_id=correlation_id,
        payload=Payload(
            file_key=file_key, bucket_name=bucket, content_type=content_type
        ),
    )


class DocumentOrchestrator:
    def __init__(
        self,
        ingestion: IngestionPipeline,
        resolution: EntityResolutionPipeline,
        storage: StorageBase,
        producer: AIOKafkaProducer,
        notification_topic: str,
    ):
        self.ingestion = ingestion
        self.resolution = resolution
        self.storage = storage
        self.producer = producer
        self.notification_topic = notification_topic

    async def process_document(self, message: Message, message_key: str) -> bool:

        event_type = message.event_type.upper()

        if event_type in (
            DocumentEventType.DOCUMENT_CREATED,
            DocumentEventType.DOCUMENT_UPDATED,
        ):
            is_update = event_type == DocumentEventType.DOCUMENT_UPDATED
            return await self.handle_creation(
                message=message, message_key=message_key, is_update=is_update
            )
        elif event_type == DocumentEventType.DOCUMENT_DELETED:
            return await self.handle_deletion(message=message, message_key=message_key)
        else:
            logger.warning(f"Unsupported event type: {event_type}")
            return True

    async def handle_creation(
        self, message: Message, message_key: str, is_update: bool = False
    ) -> bool:
        bucket, file_key, file_path = _extract_file_data(message)
        graph_file_key = _build_graph_filename(message_key, file_key)

        if not await self.storage.exists(bucket, file_key):
            return True

        if await self.storage.exists(bucket, graph_file_key):
            logger.info(
                f"Graph file {graph_file_key} already exists for document {file_key}. Skipping ingestion."
            )
            return True

        try:
            await self.storage.download_to_file(bucket, file_key, file_path)
            if not await self.storage.exists(bucket, file_key):
                logger.info(
                    f"Document file {file_key} deleted during download phase. Aborting."
                )
                return True

            chunk_graphs, chunks = await self.ingestion.ingest(
                file_path=file_path,
                bucket=bucket,
                file_key=file_key,
                message_key=message_key,
                is_update=is_update,
            )
            document_graph = await self.resolution.resolve_entities(
                file_key=file_key, chunk_graphs=chunk_graphs, chunks=chunks
            )

            await self.storage.save_file(
                bucket,
                graph_file_key,
                document_graph.model_dump(mode="json"),
            )

            response = _build_response_message(
                correlation_id=message_key,
                file_key=graph_file_key,
                bucket=bucket,
                event_type=DocumentEventType.DOCUMENT_COMPLETED,
                aggregate_type=message.aggregate_type,
            )

            await self.producer.send_and_wait(
                topic=self.notification_topic,
                value=response.model_dump_json().encode("utf-8"),
                key=file_key.encode("utf-8"),
            )

            return True
        except Exception as e:
            logger.exception(f"Document creation pipeline failed for {file_key}: {e}")
            return False
        finally:
            file_path.unlink(missing_ok=True)

    async def handle_deletion(self, message: Message, message_key: str) -> bool:
        bucket, file_key, _ = _extract_file_data(message)

        try:
            await self.delete_all_related_files(
                bucket=bucket, message_key=message_key, file_key=file_key
            )

            response = _build_response_message(
                correlation_id=message_key,
                file_key=file_key,
                bucket=bucket,
                aggregate_type=message.aggregate_type,
                event_type=DocumentEventType.DOCUMENT_DELETED,
            )

            # Send a notification about the deletion to graph database ingestion worker
            await self.producer.send_and_wait(
                topic=self.notification_topic,
                value=response.model_dump_json().encode("utf-8"),
                key=file_key.encode("utf-8"),
            )
            return True
        except Exception as e:
            logger.exception(f"Failed to clean up deleted document {file_key}: {e}")
            return False

    async def delete_all_related_files(
        self, bucket: str, message_key: str, file_key: str
    ) -> None:

        keys_to_delete = [
            build_filename_with_prefix_folder(
                f"{message_key}/intermediate/chunks", file_key
            ),
            f"{message_key}/intermediate/extractions/",
            _build_graph_filename(message_key, file_key),
        ]

        for key in keys_to_delete:
            try:
                await self.storage.delete_target(bucket, key)
            except Exception as e:
                logger.debug(f"Skipping delete for {key}: {e}")
