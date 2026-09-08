import logging

from pydantic import ValidationError

from aiokafka import AIOKafkaConsumer
from config.config import AppSettings
from models.broker import Message

logger = logging.getLogger(__name__)


class AsyncKafkaConsumer:

    def __init__(self, config: AppSettings):
        self.consumer = AIOKafkaConsumer(
            config.kafka.KAFKA_CONSUMER_TOPIC,
            bootstrap_servers=config.kafka.KAFKA_BROKER,
            group_id=config.kafka.KAFKA_CONSUMER_GROUP,
            auto_offset_reset="earliest",
            enable_auto_commit=False,
        )

    async def start(self):
        await self.consumer.start()

    async def stop(self):
        if self.consumer:
            await self.consumer.stop()

    async def consume_messages(self, async_callback):
        logger.info(f"Starting async consumer loop...")

        async for msg in self.consumer:
            try:
                payload = msg.value.decode("utf-8")
                message_key = msg.key.decode("utf-8")
                message = Message.model_validate_json(payload)

                logger.info(f"Kafka pulled payload id: {message.id}")

                success = await async_callback(message, message_key)
                if success:
                    await self.consumer.commit()
                    logger.info(
                        f"Committed offset {msg.offset} for partition {msg.partition}"
                    )
                else:
                    logger.error(
                        f"Processing failed for offset {msg.offset} in partition {msg.partition}"
                    )
                    await self.consumer.commit()

            except (ValueError, ValidationError, TypeError) as e:
                logger.error(f"Failed to decode message {payload}: {e}")
                await self.consumer.commit()
            except Exception as e:
                logger.exception(f"Unexpected error occurred: {e}")
                break
