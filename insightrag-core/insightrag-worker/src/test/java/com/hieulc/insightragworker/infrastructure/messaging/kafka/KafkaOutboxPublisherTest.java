package com.hieulc.insightragworker.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.hieulc.insightragworker.config.properties.KafkaTopicsProperties;
import com.hieulc.insightragworker.outbox.enums.AggregateType;
import com.hieulc.insightragworker.outbox.enums.EventType;
import com.hieulc.insightragworker.outbox.model.OutboxEvent;
import com.hieulc.insightragworker.outbox.model.PublishResult;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxPublisherTest {

  @Mock private KafkaTemplate<String, String> kafkaTemplate;
  @Mock private KafkaTopicsProperties kafkaTopicsProperties;
  @InjectMocks KafkaOutboxPublisher publisher;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    publisher = new KafkaOutboxPublisher(objectMapper, kafkaTemplate, kafkaTopicsProperties);
  }

  @Test
  void should_map_json_correctly_and_return_publish_result_on_success() throws Exception {
    String topic = "document.outbox";
    when(kafkaTopicsProperties.outboxEvent()).thenReturn(topic);

    OutboxEvent outboxEvent =
        createOutboxEvent("outbox-id", "document-id", "{\"file_key\":\"document.pdf\"}");

    RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
    SendResult<String, String> mockSendResult =
        new SendResult<>(new ProducerRecord<>(topic, "document-id", "mock-data"), metadata);

    when(kafkaTemplate.send(eq(topic), eq("document-id"), anyString()))
        .thenReturn(CompletableFuture.completedFuture(mockSendResult));

    CompletableFuture<PublishResult> futureResult = publisher.publish(outboxEvent);
    PublishResult result = futureResult.get();

    assertThat(result).isNotNull();
    assertThat(result.messageId()).isEqualTo("0");
    assertThat(result.topic()).isEqualTo(topic);

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    verify(kafkaTemplate).send(eq(topic), eq("document-id"), jsonCaptor.capture());

    String actualJson = jsonCaptor.getValue();

    assertThat(actualJson).contains("\"id\":\"outbox-id\"");
    assertThat(actualJson).contains("\"aggregate_type\":\"DOCUMENT\"");
    assertThat(actualJson).contains("\"event_type\":\"NEW\"");
    assertThat(actualJson).contains("\"payload\":{\"file_key\":\"document.pdf\"}");
  }

  @Test
  void should_return_failed_future_when_kafka_async_publish_fail() {
    when(kafkaTopicsProperties.outboxEvent()).thenReturn("outbox");

    OutboxEvent outboxEvent = createOutboxEvent("outbox-id", "document-id", "{}");

    CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new RuntimeException("Kafka broker down"));

    when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

    CompletableFuture<PublishResult> futureResult = publisher.publish(outboxEvent);

    assertThat(futureResult)
        .isCompletedExceptionally()
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCauseInstanceOf(RuntimeException.class)
        .withMessageContaining("Kafka broker down");
  }

  @Test
  void should_catch_and_return_failed_future_on_synchronous_exception() {
    when(kafkaTopicsProperties.outboxEvent()).thenReturn("outbox");

    OutboxEvent outboxEvent = createOutboxEvent("outbox-drop", "document-fail-id", "{}");

    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("Producer is closed"));

    CompletableFuture<PublishResult> result = publisher.publish(outboxEvent);

    assertThat(result)
        .isCompletedExceptionally()
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCauseInstanceOf(RuntimeException.class)
        .withMessageContaining("Producer is closed");
  }

  private OutboxEvent createOutboxEvent(String eventId, String aggregateId, String data) {
    return new OutboxEvent(
        eventId, AggregateType.DOCUMENT.toString(), aggregateId, EventType.NEW.toString(), data);
  }
}
