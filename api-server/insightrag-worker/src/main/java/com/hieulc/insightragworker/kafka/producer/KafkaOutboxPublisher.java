package com.hieulc.insightragworker.kafka.producer;

import com.hieulc.insightragworker.config.KafkaConfig;
import com.hieulc.insightragworker.dto.OutboxEvent;
import com.hieulc.insightragworker.port.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.util.RawValue;

import java.util.concurrent.CompletableFuture;


@Component
@RequiredArgsConstructor
public class KafkaOutboxPublisher implements OutboxEventPublisher {

    private final ObjectMapper objectMapper;

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public CompletableFuture<SendResult<String, String>> publish(OutboxEvent outboxEvent) {
        try {
            return kafkaTemplate.send(KafkaConfig.OUTBOX_TOPIC, outboxEvent.aggregateId(), mapOutboxEventToJson(outboxEvent));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

    }

    private String mapOutboxEventToJson(OutboxEvent outboxEvent){
        ObjectNode message = objectMapper.createObjectNode();
        message.put("id", outboxEvent.eventId());
        message.put("aggregate_type", outboxEvent.aggregateType());
        message.put("event_type", outboxEvent.eventType());
        message.putRawValue("payload", new RawValue(outboxEvent.data()));

        return message.toString();
    }

}
