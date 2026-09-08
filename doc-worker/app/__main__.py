import logging
import asyncio

from functools import partial

from aiokafka import AIOKafkaProducer

from config.config import get_settings
from llm.llm_factory import get_llm_provider

from pipeline.container import (
    build_registry_pipeline,
    build_ingestion_pipeline,
    build_merge_pipeline,
)

from messaging.consumer import AsyncKafkaConsumer
from storage.storage_factory import get_storage
from pipeline.orchestrator import process_document
from embedding.transformer import Embedder

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


async def setup_dependencies():

    settings = get_settings()
    storage = get_storage(settings)

    ingestion_llm = get_llm_provider(
        settings=settings,
        model_name=settings.gen_ai.INGESTION_MODEL_NAME,
    )
    if hasattr(ingestion_llm, "init"):
        await ingestion_llm.init()

    resolution_llm = get_llm_provider(
        settings=settings,
        model_name=settings.gen_ai.RESOLUTION_MODEL_NAME,
    )
    if hasattr(resolution_llm, "init"):
        await resolution_llm.init()

    embedder = Embedder(settings)
    registry = build_registry_pipeline(resolution_llm, settings)
    ingestion_pipeline = build_ingestion_pipeline(
        settings, storage, ingestion_llm, embedder, registry
    )
    entity_resolution = build_merge_pipeline(settings, resolution_llm, embedder)

    consumer = AsyncKafkaConsumer(settings)
    producer = AIOKafkaProducer(bootstrap_servers=settings.kafka.KAFKA_BROKER)

    await consumer.start()
    await producer.start()

    return settings, ingestion_pipeline, entity_resolution, storage, consumer, producer


async def main():
    logger.info("Initializing DocumentWorker...")

    settings, ingestion, resolution, storage, consumer, producer = (
        await setup_dependencies()
    )

    orchestrator_callback = partial(
        process_document,
        notification_topic=settings.kafka.KAFKA_PRODUCER_TOPIC,
        ingestion=ingestion,
        resolution=resolution,
        storage=storage,
        producer=producer,
    )

    try:
        await consumer.consume_messages(orchestrator_callback)
    except Exception as e:
        logger.error(f"Error occurred while consuming messages: {e}")
    finally:
        logger.info("Cleaning up Kafka connections...")
        await consumer.stop()
        await producer.stop()


if __name__ == "__main__":
    asyncio.run(main())
