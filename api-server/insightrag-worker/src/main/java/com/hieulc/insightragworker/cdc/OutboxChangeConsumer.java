package com.hieulc.insightragworker.cdc;

import com.hieulc.insightragworker.dto.OutboxEvent;
import com.hieulc.insightragworker.dto.PublishResult;
import com.hieulc.insightragworker.entity.OutboxDeadLetterEvent;
import com.hieulc.insightragworker.port.OutboxEventPublisher;
import com.hieulc.insightragworker.repository.OutboxDeadLetterRepository;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxChangeConsumer implements DebeziumEngine.ChangeConsumer<ChangeEvent<String, String>> {

    private final OutboxEventPublisher outboxEventPublisher;
    private final ObjectMapper objectMapper;
    private final OutboxDeadLetterRepository deadLetterRepository;

    @Override
    public void handleBatch(List<ChangeEvent<String, String>> records,
                            DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter) {
        List<PublishResult> publishResults = new ArrayList<>();

        try {
            for (var record : records) {

                if(isHeartbeat(record)){
                    recordCommitter.markProcessed(record);
                    continue;
                }

                if (record.value() == null) {
                    recordCommitter.markProcessed(record);
                    continue;
                }

//                log.debug("Record key {}/ value: {}/ topic: {}", record.key(), record.value(), record.destination());
                OutboxEvent outboxEvent = parseOutboxEvent(record.value());

                if (outboxEvent == null) {
                    recordCommitter.markProcessed(record);
                    continue;
                }

                PublishResult result = outboxEventPublisher.publish(outboxEvent).join();
                publishResults.add(result);

                log.debug("Record {} successfully published to topic {}", result.messageId(), result.topic());

                recordCommitter.markProcessed(record);
            }

            recordCommitter.markBatchFinished();

            if(!publishResults.isEmpty()){
                log.info("Successfully pushed and commited batch of {} outbox event to Kafka", publishResults.size());
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
                    nestedPayload
            );
        } catch (Exception e) {
            log.error("Corrupted outbox event skipped. Data {}", jsonString, e);

            deadLetterRepository.save(new OutboxDeadLetterEvent(
                    jsonString,
                    e.getMessage() != null ? e.getMessage() : "Unknown parsing error"
            ));

            return null;
        }
    }

    private boolean isHeartbeat(ChangeEvent<String, String> record){
        if(record.destination() == null || record.destination().isBlank()) return false;

        return record.destination().contains("__debezium-heartbeat");
    }
}
