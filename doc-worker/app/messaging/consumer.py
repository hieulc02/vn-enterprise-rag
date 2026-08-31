import logging
import asyncio

from concurrent.futures import ThreadPoolExecutor, Future

from pydantic import ValidationError
from confluent_kafka import (
    Consumer,
    KafkaException,
    KafkaError,
    Message as KafkaMessage,
)
from config.config import AppSettings
from models.broker import Message

logger = logging.getLogger(__name__)


class KafkaConsumer:

    def __init__(self, config: AppSettings):
        self.consumer = Consumer(
            {
                "bootstrap.servers": config.kafka.KAFKA_BROKER,
                "group.id": config.kafka.KAFKA_CONSUMER_GROUP,
                "auto.offset.reset": "earliest",
                "enable.auto.offset.store": False,
            }
        )
        self.executor = ThreadPoolExecutor(max_workers=1)

    def consume_messages(self, topic, async_callback):
        self.consumer.subscribe([topic])

        future: Future | None = None
        curr_msg: KafkaMessage | None = None

        logger.info(f"Starting consumer loop for topic: {topic}")

        def thread_bridge(message):
            return asyncio.run(async_callback(message))

        is_running: bool = True
        try:
            while is_running:
                msg = self.consumer.poll(1.0)

                if future is not None and future.done():
                    try:
                        success = future.result()
                        if success:
                            self.consumer.store_offsets(curr_msg)
                            self.consumer.commit(message=curr_msg, asynchronous=False)
                            logger.info(f"Committed offset for {curr_msg.offset()}")
                        else:
                            logger.error(
                                f"Processing failed for offset {curr_msg.offset()}"
                            )
                            self.consumer.store_offsets(curr_msg)
                            self.consumer.commit(message=curr_msg, asynchronous=False)

                    except Exception as e:
                        logger.exception(f"Worker thread crashed fatally: {e}")
                        is_running = False
                        continue

                    future = None
                    curr_msg = None

                    active_partitions = self.consumer.assignment()
                    self.consumer.resume(active_partitions)
                    logger.debug(f"Resume Kafka partitions {active_partitions}")

                if msg is None:
                    continue

                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        continue

                    logger.error(f"Consumer error: {msg.error()}")
                    raise KafkaException(msg.error())

                if future is not None:
                    continue

                try:
                    message = Message.model_validate_json(msg.value())

                    logger.info(
                        f"Kafka pulled payload id: {message.id}. Offloading to worker thread"
                    )

                    paused_partitions = self.consumer.assignment()
                    self.consumer.pause(paused_partitions)
                    curr_msg = msg
                    future = self.executor.submit(thread_bridge, message)

                except (ValueError, ValidationError, TypeError) as e:
                    logger.error(
                        f"Failed to decode message: {msg.value().decode('utf-8')}"
                    )
                    self.consumer.store_offsets(msg)
                    self.consumer.commit(message=msg, asynchronous=False)
                except Exception as e:
                    logger.exception(f"Unexpected error occurred: {e}")
                    is_running = False

        except KeyboardInterrupt:
            logger.info("Gracefully shutting down consumer...")
        finally:
            self.executor.shutdown(wait=True)
            self.consumer.close()
