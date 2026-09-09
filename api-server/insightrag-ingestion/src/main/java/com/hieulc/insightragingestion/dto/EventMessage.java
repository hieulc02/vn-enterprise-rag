package com.hieulc.insightragingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EventMessage(
        String id,
        @JsonProperty("aggregate_type") String aggregateType,
        @JsonProperty("event_type") String eventType,
        EventPayload payload,
        String timestamp,
        @JsonProperty("correlation_id") String correlationId
) {
}
