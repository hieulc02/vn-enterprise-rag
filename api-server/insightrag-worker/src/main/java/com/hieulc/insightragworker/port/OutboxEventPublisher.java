package com.hieulc.insightragworker.port;

import com.hieulc.insightragworker.dto.OutboxEvent;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

public interface OutboxEventPublisher {
    CompletableFuture<SendResult<String, String>> publish(OutboxEvent outboxEvent);
}
