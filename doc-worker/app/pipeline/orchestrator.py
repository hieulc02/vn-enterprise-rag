import logging
import tempfile
import mimetypes


from aiokafka import AIOKafkaProducer

from pipeline.ingest import IngestionPipeline
from pipeline.resolution import EntityResolutionPipeline

from storage.base import StorageBase

from models.broker import Payload, Message, ResponseMessage
from pathlib import Path

logger = logging.getLogger(__name__)


def _build_tmp_path(file_key: str, extension: str) -> Path:
    sys_temp_dir = Path(tempfile.gettempdir())
    if extension and not extension.startswith("."):
        extension = mimetypes.guess_extension(extension)
    elif not extension:
        extension = Path(file_key).suffix
    return sys_temp_dir / f"{Path(file_key).stem}{extension}"


def _extract_file_data(message: Message):
    bucket = message.payload.bucket_name
    file_key = message.payload.file_key
    extension = message.payload.extension
    file_path = _build_tmp_path(file_key, extension)
    return bucket, file_key, file_path


def _build_graph_filename(file_key: str, extension: str = ".json") -> str:
    graph_prefix = "graph_"
    return f"{graph_prefix}{Path(file_key).stem}{extension}"


def _buid_response_message(
    correlation_id: str,
    file_key: str,
    bucket: str,
    aggregate_type: str = "DOCUMENT",
    event_type: str = "DOCUMENT_COMPLETED",
    extension: str = ".json",
):
    return ResponseMessage(
        aggregate_type=aggregate_type,
        event_type=event_type,
        correlation_id=correlation_id,
        payload=Payload(file_key=file_key, bucket_name=bucket, extension=extension),
    )


async def process_document(
    message: Message,
    message_key: str,
    notification_topic: str,
    ingestion: IngestionPipeline,
    resolution: EntityResolutionPipeline,
    storage: StorageBase,
    producer: AIOKafkaProducer,
) -> bool:

    bucket, file_key, file_path = _extract_file_data(message)

    if not await storage.exists(bucket, file_key):
        return False

    try:
        await storage.download_to_file(bucket, file_key, file_path)

        chunk_graphs, chunks = await ingestion.ingest(file_path, bucket, file_key)

        document_graph = await resolution.resolve_entities(
            file_key=file_key, chunk_graphs=chunk_graphs, chunks=chunks
        )

        graph_file_key = _build_graph_filename(file_key)

        await storage.save_file(
            bucket,
            graph_file_key,
            document_graph.model_dump(mode="json"),
        )

        response = _buid_response_message(
            correlation_id=message_key,
            file_key=graph_file_key,
            bucket=bucket,
            aggregate_type=message.aggregate_type,
        )

        await producer.send_and_wait(
            topic=notification_topic,
            value=response.model_dump_json().encode("utf-8"),
            key=file_key.encode("utf-8"),
        )

        return True
    except Exception as e:
        logger.error(f"Pipeline failed for {file_key}: {e}")
        return False
    finally:
        file_path.unlink(missing_ok=True)
