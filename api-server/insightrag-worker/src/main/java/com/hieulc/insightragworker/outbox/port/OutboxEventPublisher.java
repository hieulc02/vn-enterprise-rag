package com.hieulc.insightragworker.outbox.port;

import com.hieulc.insightragworker.outbox.model.OutboxEvent;
import com.hieulc.insightragworker.outbox.model.PublishResult;
import java.util.concurrent.CompletableFuture;

public interface OutboxEventPublisher {
  CompletableFuture<PublishResult> publish(OutboxEvent outboxEvent);
}
