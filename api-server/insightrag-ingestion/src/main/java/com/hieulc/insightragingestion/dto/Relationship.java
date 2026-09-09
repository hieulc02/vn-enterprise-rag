package com.hieulc.insightragingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Set;

public record Relationship(
        String id,
        String source,
        String target,
        String type,
        String description,
        double weight,
        @JsonProperty("source_chunk_ids") Set<String> sourceChunkIds,
        Map<String, Object> properties) {
}
