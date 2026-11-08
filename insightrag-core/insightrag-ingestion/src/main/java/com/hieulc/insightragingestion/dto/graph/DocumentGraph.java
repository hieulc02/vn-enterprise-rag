package com.hieulc.insightragingestion.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DocumentGraph(
    @JsonProperty("document_key") String documentKey,
    List<DocumentChunk> chunks,
    List<Entity> entities,
    List<Relationship> relationships) {}
