package com.hieulc.insightragingestion.dto.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hieulc.insightragingestion.enums.EventType;

public record EventMessage(
    String id,
    String timestamp,
    @JsonProperty("correlation_id") String correlationId,
    @JsonProperty("aggregate_type") String aggregateType,
    @JsonProperty("event_type") EventType eventType,
    EventPayload payload) {}
