import pytest

from unittest.mock import patch, MagicMock, AsyncMock

from app.messaging.consumer import AsyncKafkaConsumer


@pytest.fixture
def mock_settings():
    settings = MagicMock()
    settings.kafka.KAFKA_CONSUMER_TOPIC = "test-topic"
    settings.kafka.KAFKA_BROKER = "localhost:9092"
    settings.kafka.KAFKA_CONSUMER_GROUP = "test_group"
    settings.kafka.KAFKA_PRODUCER_TOPIC_DLQ = "test-topic-dlq"
    return settings


@pytest.fixture
def mock_producer():
    producer = AsyncMock()
    return producer


@pytest.fixture
def valid_message_payload():
    return b"""
    {
        "id": "123",
        "aggregate_type": "DOCUMENT",
        "event_type": "NEW",
        "payload": {
            "file_key": "test.pdf",
            "bucket_name": "test-bucket",
            "content_type": "application/pdf"
        }
    }
    """


class FakeKafkaConsumer:
    def __init__(self, messages):
        self.messages = messages
        self.commit = AsyncMock()
        self.start = AsyncMock()
        self.stop = AsyncMock()

    async def __aiter__(self):
        for msg in self.messages:
            yield msg


class TestAsyncKafkaConsumer:

    @pytest.mark.asyncio
    @patch("app.messaging.consumer.AIOKafkaConsumer")
    @patch("asyncio.sleep", new_callable=AsyncMock)
    async def test_consume_messages_transient_failure_recovery(
        self,
        mock_sleep,
        MockAIOKafkaConsumer,
        mock_settings,
        mock_producer,
        valid_message_payload,
    ):
        mock_msg = MagicMock()
        mock_msg.id = "test-id"
        mock_msg.value = valid_message_payload
        mock_msg.key = b"test-key"
        mock_msg.offset = 1
        mock_msg.partition = 0

        mock_callback = AsyncMock(side_effect=[False, False, True])

        consumer = AsyncKafkaConsumer(mock_settings, mock_producer)

        fake_kafka = FakeKafkaConsumer([mock_msg])
        consumer.consumer = fake_kafka

        await consumer.consume_messages(mock_callback)

        assert mock_callback.call_count == 3
        assert mock_sleep.call_count == 2
        mock_producer.send_and_wait.assert_not_called()
        fake_kafka.commit.assert_called_once()

    @pytest.mark.asyncio
    @patch("app.messaging.consumer.AIOKafkaConsumer")
    async def test_consume_messages_retries_exhaustion_and_routes_to_dlq(
        self, MockAIOKafkaConsumer, mock_settings, mock_producer, valid_message_payload
    ):
        mock_msg = MagicMock()
        mock_msg.value = valid_message_payload
        mock_msg.key = b"test-key"

        mock_callback = AsyncMock(return_value=False)

        consumer = AsyncKafkaConsumer(mock_settings, mock_producer)

        fake_kafka = FakeKafkaConsumer([mock_msg])
        consumer.consumer = fake_kafka

        await consumer.consume_messages(mock_callback)
        mock_producer.send_and_wait.assert_called_once()

        call_kwargs = mock_producer.send_and_wait.call_args.kwargs
        assert call_kwargs["topic"] == "test-topic-dlq"
        assert call_kwargs["value"] == valid_message_payload
        assert call_kwargs["headers"] == [
            ("dlq_reason", b"Exceeded 3 processing retries")
        ]

    @pytest.mark.asyncio
    @patch("app.messaging.consumer.AIOKafkaConsumer")
    async def test_consume_messages_poison_pill_routes_directly_to_dlq(
        self, MockAIOKafkaConsumer, mock_settings, mock_producer, valid_message_payload
    ):
        mock_msg = MagicMock()
        mock_msg.value = b'{"invalid_key":"missing"}'
        mock_msg.key = b"test-key"

        mock_callback = AsyncMock()

        consumer = AsyncKafkaConsumer(mock_settings, mock_producer)

        fake_kafka = FakeKafkaConsumer([mock_msg])
        consumer.consumer = fake_kafka

        await consumer.consume_messages(mock_callback)

        mock_callback.assert_not_called()
        mock_producer.send_and_wait.assert_called_once()

        call_kwargs = mock_producer.send_and_wait.call_args.kwargs
        assert b"Validation error" in call_kwargs["headers"][0][1]
        fake_kafka.commit.assert_called_once()
