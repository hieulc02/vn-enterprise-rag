package com.hieulc.insightragworker.infrastructure.cdc.debezium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hieulc.insightragworker.outbox.model.OutboxDeadLetterEvent;
import com.hieulc.insightragworker.outbox.model.OutboxEvent;
import com.hieulc.insightragworker.outbox.model.PublishResult;
import com.hieulc.insightragworker.outbox.port.OutboxDeadLetterRepository;
import com.hieulc.insightragworker.outbox.port.OutboxEventPublisher;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxChangeConsumerTest {

  @Mock private OutboxEventPublisher outboxEventPublisher;
  @Mock private OutboxDeadLetterRepository deadLetterRepository;
  @Mock private DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter;
  @Mock private ChangeEvent<String, String> mockRecord;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private OutboxChangeConsumer outboxChangeConsumer;

  @BeforeEach
  void setUp() {
    outboxChangeConsumer =
        new OutboxChangeConsumer(outboxEventPublisher, objectMapper, deadLetterRepository);
  }

  @Test
  void handleBatch_skips_and_commit_on_heartbeat_events() throws InterruptedException {
    when(mockRecord.destination()).thenReturn("test-server.__debezium-heartbeat");
    outboxChangeConsumer.handleBatch(List.of(mockRecord), recordCommitter);

    verify(recordCommitter).markProcessed(mockRecord);
    verify(recordCommitter).markBatchFinished();
    verifyNoInteractions(outboxEventPublisher, deadLetterRepository);
  }

  @Test
  void handleBatch_skips_and_commit_tombstone_null_event() throws InterruptedException {
    when(mockRecord.destination()).thenReturn("test.outbox");
    when(mockRecord.value()).thenReturn(null);

    outboxChangeConsumer.handleBatch(List.of(mockRecord), recordCommitter);

    verify(recordCommitter).markProcessed(mockRecord);
    verify(recordCommitter).markBatchFinished();
    verifyNoInteractions(outboxEventPublisher, deadLetterRepository);
  }

  @Test
  void handleBatch_skips_delete_events_missing_after_node() throws InterruptedException {

    String deleteJson =
        """
      {
        "payload": {
          "before": { "id": "123" },
          "after": null
        }
      }
      """;

    when(mockRecord.destination()).thenReturn("test.outbox");
    when(mockRecord.value()).thenReturn(deleteJson);

    outboxChangeConsumer.handleBatch(List.of(mockRecord), recordCommitter);

    verify(recordCommitter).markProcessed(mockRecord);
    verifyNoInteractions(outboxEventPublisher);
  }

  @Test
  void handleBatch_saves_to_dlq_when_json_is_corrupted() throws InterruptedException {

    String malformedJson = "{ invalid_json: }";

    when(mockRecord.destination()).thenReturn("test.outbox");
    when(mockRecord.value()).thenReturn(malformedJson);

    outboxChangeConsumer.handleBatch(List.of(mockRecord), recordCommitter);

    verify(recordCommitter).markProcessed(mockRecord);

    ArgumentCaptor<OutboxDeadLetterEvent> captor =
        ArgumentCaptor.forClass(OutboxDeadLetterEvent.class);

    verify(deadLetterRepository).save(captor.capture());
    assertThat(captor.getValue().getFailedPayload()).isEqualTo(malformedJson);
    verifyNoInteractions(outboxEventPublisher);
  }

  @Test
  void handleBatch_publishes_and_commit_valid_insert_event() throws InterruptedException {

    String validJson =
        """
        {
          "payload": {
            "after": {
              "id": "event-id",
              "aggregate_type": "DOCUMENT",
              "aggregate_id": "doc-123",
              "event_type": "CREATED",
              "payload": "{\\"key\\": \\"value\\"}"
            }
          }
        }
        """;

    when(mockRecord.destination()).thenReturn("test.outbox");
    when(mockRecord.value()).thenReturn(validJson);

    PublishResult result = new PublishResult("message-id", "topic");
    when(outboxEventPublisher.publish(any(OutboxEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(result));

    outboxChangeConsumer.handleBatch(List.of(mockRecord), recordCommitter);

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventPublisher).publish(captor.capture());
    OutboxEvent captured = captor.getValue();
    assertThat(captured.aggregateId()).isEqualTo("doc-123");
    assertThat(captured.aggregateType()).isEqualTo("DOCUMENT");

    verify(recordCommitter).markProcessed(mockRecord);
    verifyNoInteractions(deadLetterRepository);
  }

  @Test
  void handleBatch_halts_debezium_when_kafka_publish_fails() throws InterruptedException {

    String validJson =
        """
        {
          "payload": {
            "after": {
              "id": "event-id",
              "aggregate_type": "DOCUMENT",
              "aggregate_id": "doc-123",
              "event_type": "CREATED",
              "payload": "{}"
            }
          }
        }
        """;

    when(mockRecord.destination()).thenReturn("test.outbox");
    when(mockRecord.value()).thenReturn(validJson);

    CompletableFuture<PublishResult> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(
        new CompletionException(new RuntimeException("Kafka timeout")));
    when(outboxEventPublisher.publish(any(OutboxEvent.class))).thenReturn(failedFuture);

    assertThatThrownBy(() -> outboxChangeConsumer.handleBatch(List.of(mockRecord), recordCommitter))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("CDC Batch publish fail");

    verify(recordCommitter, never()).markBatchFinished();
    verify(recordCommitter, never()).markBatchFinished();
  }
}
