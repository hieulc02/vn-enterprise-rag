package com.hieulc.insightragworker.port;

import com.hieulc.insightragworker.dto.OutboxEvent;
import com.hieulc.insightragworker.dto.PublishResult;

import java.util.concurrent.CompletableFuture;

public interface OutboxEventPublisher {
    CompletableFuture<PublishResult> publish(OutboxEvent outboxEvent);
}
