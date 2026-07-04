package com.hieulc.insightragworker.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OutboxEvent(
        String eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String data
){}
