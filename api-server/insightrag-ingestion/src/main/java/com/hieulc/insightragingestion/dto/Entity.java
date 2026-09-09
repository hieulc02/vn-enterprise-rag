package com.hieulc.insightragingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record Entity(
        String id,
        String title,
        List<String> aliases,
        List<String> labels,
        String description,
        @JsonProperty("source_chunk_ids") Set<String> sourceChunkIds,
        Map<String, Object> properties
) {
}
