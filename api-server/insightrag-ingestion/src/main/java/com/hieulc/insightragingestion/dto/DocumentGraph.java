package com.hieulc.insightragingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DocumentGraph(
        @JsonProperty("document_id") String documentId,
        List<DocumentChunk> chunks,
        List<Entity> entities,
        List<Relationship> relationships
) {
}
