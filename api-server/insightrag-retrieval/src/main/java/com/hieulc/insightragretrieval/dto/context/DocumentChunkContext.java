package com.hieulc.insightragretrieval.dto.context;

import java.util.List;

public record DocumentChunkContext(
    String chunkId,
    String text,
    ChunkMetadata metadata,
    List<String> linkedEntities,
    double wrrfScore) {
  public record ChunkMetadata(String documentId, Integer pageNumber) {}
}
