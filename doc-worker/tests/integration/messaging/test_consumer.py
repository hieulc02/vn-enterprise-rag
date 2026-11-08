import pytest
import asyncio

from unittest.mock import AsyncMock

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

from testcontainers.core.container import DockerContainer
from testcontainers.core.waiting_utils import wait_for_logs

from app.config.config import AppSettings
from app.messaging.consumer import AsyncKafkaConsumer
from aiokafka.admin import AIOKafkaAdminClient, NewTopic


@pytest.fixture(scope="module")
def kafka_broker():
    container = DockerContainer("docker.redpanda.com/redpandadata/redpanda:v26.1.18")
    container.with_bind_ports(9092, 9092)

    container.with_command(
        "redpanda start --smp 1 "
        "--kafka-addr PLAINTEXT://0.0.0.0:9092 "
        "--advertise-kafka-addr PLAINTEXT://localhost:9092"
    )

    with container as redpanda:
        wait_for_logs(redpanda, r".*Successfully started Redpanda!.*", timeout=30)
        yield "localhost:9092"


@pytest.fixture
def test_settings(kafka_broker):
    settings = AppSettings()
    settings.kafka.KAFKA_BROKER = kafka_broker
    settings.kafka.KAFKA_CONSUMER_TOPIC = "test-docs"
    settings.kafka.KAFKA_CONSUMER_GROUP = "test_group"
    settings.kafka.KAFKA_PRODUCER_TOPIC_DLQ = "test-docs-dlq"
    return settings


@pytest.mark.asyncio
async def test_consume_messages_real_kafka_on_success(test_settings):

    admin = AIOKafkaAdminClient(bootstrap_servers=test_settings.kafka.KAFKA_BROKER)
    await admin.start()

    main_topic = NewTopic(
        name=test_settings.kafka.KAFKA_CONSUMER_TOPIC,
        num_partitions=1,
        replication_factor=1,
    )

    await admin.create_topics([main_topic])
    await admin.close()

    producer = AIOKafkaProducer(bootstrap_servers=test_settings.kafka.KAFKA_BROKER)
    await producer.start()

    consumer = AsyncKafkaConsumer(test_settings, producer)
    await consumer.start()

    test_payload = b"""{
        "id": "123",
        "aggregate_type": "DOCUMENT",
        "event_type": "NEW",
        "payload": {
            "file_key": "test.pdf",
            "bucket_name": "test",
            "content_type": "application/pdf"
        }
    }"""

    await producer.send_and_wait(
        topic=test_settings.kafka.KAFKA_CONSUMER_TOPIC,
        value=test_payload,
        key=b"test-key",
    )

    received_message = None

    async def mock_orchestrator(message, key):
        nonlocal received_message
        received_message = message
        return True

    consume_task = asyncio.create_task(consumer.consume_messages(mock_orchestrator))

    try:
        await asyncio.wait_for(asyncio.sleep(3), timeout=5.0)
    finally:
        await consumer.stop()
        await producer.stop()
        consume_task.cancel()

    assert received_message is not None
    assert received_message.id == "123"
    assert received_message.aggregate_type == "DOCUMENT"
    assert received_message.event_type == "NEW"
    assert received_message.payload.file_key == "test.pdf"
    assert received_message.payload.bucket_name == "test"
    assert received_message.payload.content_type == "application/pdf"


@pytest.mark.asyncio
async def test_consume_messages_routes_to_dlq_on_invalid_payload(test_settings):
    admin = AIOKafkaAdminClient(bootstrap_servers=test_settings.kafka.KAFKA_BROKER)
    await admin.start()

    main_topic = NewTopic(
        name=test_settings.kafka.KAFKA_CONSUMER_TOPIC,
        num_partitions=1,
        replication_factor=1,
    )
    dlq_topic = NewTopic(
        name=test_settings.kafka.KAFKA_PRODUCER_TOPIC_DLQ,
        num_partitions=1,
        replication_factor=1,
    )

    await admin.create_topics([main_topic, dlq_topic])
    await admin.close()

    producer = AIOKafkaProducer(bootstrap_servers=test_settings.kafka.KAFKA_BROKER)
    await producer.start()

    consumer = AsyncKafkaConsumer(test_settings, producer)
    await consumer.start()

    dlq_inspector = AIOKafkaConsumer(
        test_settings.kafka.KAFKA_PRODUCER_TOPIC_DLQ,
        bootstrap_servers=test_settings.kafka.KAFKA_BROKER,
        group_id="dlq_inspector_group",
        auto_offset_reset="earliest",
    )
    await dlq_inspector.start()

    invalid_payload = b'{"id": "123", "event_type": "UNKNOWN"}'
    invalid_key = b"broken-document-key"

    await producer.send_and_wait(
        topic=test_settings.kafka.KAFKA_CONSUMER_TOPIC,
        value=invalid_payload,
        key=invalid_key,
    )

    mock_orchestrator = AsyncMock()
    consume_task = asyncio.create_task(consumer.consume_messages(mock_orchestrator))

    try:

        async def fetch_one_from_dlq():
            async for msg in dlq_inspector:
                return msg

        dlq_msg = await asyncio.wait_for(fetch_one_from_dlq(), timeout=10.0)

        mock_orchestrator.assert_not_called()

        assert dlq_msg.value == invalid_payload
        assert dlq_msg.key == invalid_key

        headers_dict = dict(dlq_msg.headers)
        assert "dlq_reason" in headers_dict
        assert b"Validation error" in headers_dict["dlq_reason"]

    finally:
        consume_task.cancel()
        await dlq_inspector.stop()
        await consumer.stop()
        await producer.stop()
