package com.hieulc.insightragworker.outbox.model;

public record PublishResult(String messageId, String topic) {}
