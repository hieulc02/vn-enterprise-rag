import logging
import asyncio
from pydantic import ValidationError

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from config.config import AppSettings
from models.broker import Message

logger = logging.getLogger(__name__)


class AsyncKafkaConsumer:

    def __init__(self, config: AppSettings, producer: AIOKafkaProducer):
        self.consumer = AIOKafkaConsumer(
            config.kafka.KAFKA_CONSUMER_TOPIC,
            bootstrap_servers=config.kafka.KAFKA_BROKER,
            group_id=config.kafka.KAFKA_CONSUMER_GROUP,
            auto_offset_reset="earliest",
            enable_auto_commit=False,
            max_poll_interval_ms=300000,
            session_timeout_ms=60000,
            heartbeat_interval_ms=20000,
        )
        self.producer = producer
        self.dlq_topic = config.kafka.KAFKA_PRODUCER_TOPIC_DLQ
        self.max_retries = 3

    async def start(self):
        await self.consumer.start()

    async def stop(self):
        if self.consumer:
            await self.consumer.stop()

    async def send_to_dlq(self, value: bytes, key: bytes, reason: str):
        try:
            headers = [("dlq_reason", reason.encode("utf-8"))]
            await self.producer.send_and_wait(
                topic=self.dlq_topic, value=value, key=key, headers=headers
            )
        except Exception as e:
            logger.error(f"Failed to write to DLQ: {e}")
            raise

    async def consume_messages(self, async_callback):
        logger.info(f"Starting async consumer loop...")

        async for msg in self.consumer:
            payload = msg.value.decode("utf-8")
            message_key_bytes = msg.key or b""
            message_key = message_key_bytes.decode("utf-8")
            try:
                message = Message.model_validate_json(payload)
                logger.info(f"Kafka pulled payload id: {message.id}")

                success = False
                for attempt in range(1, self.max_retries + 1):
                    logger.info(
                        f"Processing message {message.id}, attempt {attempt}/{self.max_retries}"
                    )

                    success = await async_callback(message, message_key)

                    if success:
                        break

                    if attempt < self.max_retries:
                        backoff = 2**attempt
                        logger.warning(
                            f"Attempt {attempt} failed, retrying in {backoff}s..."
                        )
                        await asyncio.sleep(backoff)

                if not success:
                    await self.send_to_dlq(
                        msg.value,
                        message_key_bytes,
                        f"Exceeded {self.max_retries} processing retries",
                    )

                await self.consumer.commit()
                logger.info(
                    f"Committed offset {msg.offset} for partition {msg.partition}"
                )

            except (ValueError, ValidationError, TypeError) as e:
                logger.error(f"Failed to decode message {payload}: {e}")
                await self.send_to_dlq(
                    msg.value, message_key_bytes, f"Validation error: {e}"
                )
                await self.consumer.commit()
            except Exception as e:
                logger.exception(f"Unexpected error occurred: {e}")
                break
