package com.hieulc.insightragworker.cdc;

import com.fasterxml.jackson.core.JsonProcessingException;


import com.hieulc.insightragworker.dto.OutboxEvent;
import com.hieulc.insightragworker.kafka.producer.KafkaOutboxPublisher;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.JsonNodeException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxChangeConsumer implements DebeziumEngine.ChangeConsumer<ChangeEvent<String, String>> {

    private final KafkaOutboxPublisher kafkaOutboxPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public void handleBatch(List<ChangeEvent<String, String>> records,
                            DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter) {
        try {
            List<CompletableFuture<?>> futures = new ArrayList<>();

            for (var record : records) {
                if (record.value() == null) continue;

                OutboxEvent outboxEvent = null;
                try {
                    outboxEvent = parseOutboxEvent(record.value());
                } catch (JsonProcessingException |
                         IllegalArgumentException |
                         JsonNodeException e) {
                    log.error("Corrupted outbox event skipped. Data {}", record.value());
                }
                recordCommitter.markProcessed(record);

                if(outboxEvent == null) continue;
                futures.add(kafkaOutboxPublisher.publish(outboxEvent));
            }

            recordCommitter.markBatchFinished();

            //Check if is there any unchecked exceptions happen in the patch processing before delivering event to kafka
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();
            if(!futures.isEmpty()){
                log.info("Successfully pushed and commited batch of {} outbox event to Kafka", records.size());
            }
        } catch (CompletionException e) {
            log.error("Kafka delivery failed. Halting Debezium to prevent data loss", e);
            throw new RuntimeException("CDC Batch publish fail", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted during Kafka publish", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected fatal error happens. Halting Kafka publish", e);
        }
    }

    private OutboxEvent parseOutboxEvent(String jsonString)
            throws JsonProcessingException, IllegalArgumentException, JsonNodeException {
        JsonNode rootNode = objectMapper.readTree(jsonString);
        JsonNode payloadNode = rootNode.path("payload");

        JsonNode afterNode = payloadNode.path("after");
        if(afterNode.isMissingNode() || afterNode.isNull()){
            log.debug("Skipping non-insert event: {}", payloadNode);
            return null;
        }

        String nestedPayload = payloadNode.get("after").asString();

        return new OutboxEvent(
                afterNode.get("id").asString(),
                afterNode.get("aggregate_type").asString(),
                afterNode.get("aggregate_id").asString(),
                afterNode.get("event_type").asString(),
                nestedPayload
        );
    }
}
