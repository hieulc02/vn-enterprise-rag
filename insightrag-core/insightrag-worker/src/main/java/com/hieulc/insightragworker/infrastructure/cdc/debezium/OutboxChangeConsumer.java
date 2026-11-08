package com.hieulc.insightragworker.infrastructure.cdc.debezium;

import com.hieulc.insightragworker.outbox.model.OutboxDeadLetterEvent;
import com.hieulc.insightragworker.outbox.model.OutboxEvent;
import com.hieulc.insightragworker.outbox.model.PublishResult;
import com.hieulc.insightragworker.outbox.port.OutboxDeadLetterRepository;
import com.hieulc.insightragworker.outbox.port.OutboxEventPublisher;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxChangeConsumer
    implements DebeziumEngine.ChangeConsumer<ChangeEvent<String, String>> {

  private final OutboxEventPublisher outboxEventPublisher;
  private final ObjectMapper objectMapper;
  private final OutboxDeadLetterRepository deadLetterRepository;

  @Override
  public void handleBatch(
      List<ChangeEvent<String, String>> records,
      DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter) {
    List<CompletableFuture<PublishResult>> futures = new ArrayList<>();
    try {
      for (var record : records) {

        if (isHeartbeat(record)) {
          recordCommitter.markProcessed(record);
          continue;
        }

        if (record.value() == null) {
          recordCommitter.markProcessed(record);
          continue;
        }

        OutboxEvent outboxEvent = parseOutboxEvent(record.value());
        if (outboxEvent == null) {
          recordCommitter.markProcessed(record);
          continue;
        }

        // asynchronous cdc batch
        CompletableFuture<PublishResult> future = outboxEventPublisher.publish(outboxEvent);
        futures.add(future);
        recordCommitter.markProcessed(record);
      }

      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      recordCommitter.markBatchFinished();

      if (!futures.isEmpty()) {
        log.info(
            "Successfully pushed and commited batch of {} outbox event to Kafka", futures.size());
      }

    } catch (CompletionException e) {
      log.error("Kafka delivery failed. Halting Debezium to prevent data loss", e);
      throw new RuntimeException("CDC Batch publish fail", e);
    } catch (Exception e) {
      throw new RuntimeException("Unexpected fatal error happens. Halting Kafka publish", e);
    }
  }

  private OutboxEvent parseOutboxEvent(String jsonString) {
    try {
      JsonNode rootNode = objectMapper.readTree(jsonString);
      JsonNode payloadNode = rootNode.path("payload");

      JsonNode afterNode = payloadNode.path("after");
      if (afterNode.isMissingNode() || afterNode.isNull()) {
        log.debug("Skipping non-insert event: {}", payloadNode);
        return null;
      }

      String nestedPayload = afterNode.get("payload").asString();

      return new OutboxEvent(
          afterNode.get("id").asString(),
          afterNode.get("aggregate_type").asString(),
          afterNode.get("aggregate_id").asString(),
          afterNode.get("event_type").asString(),
          nestedPayload);
    } catch (Exception e) {
      log.error("Corrupted outbox event skipped. Data {}", jsonString, e);

      deadLetterRepository.save(
          new OutboxDeadLetterEvent(
              jsonString, e.getMessage() != null ? e.getMessage() : "Unknown parsing error"));

      return null;
    }
  }

  private boolean isHeartbeat(ChangeEvent<String, String> record) {
    if (record.destination() == null || record.destination().isBlank()) return false;

    return record.destination().contains("__debezium-heartbeat");
  }
}
