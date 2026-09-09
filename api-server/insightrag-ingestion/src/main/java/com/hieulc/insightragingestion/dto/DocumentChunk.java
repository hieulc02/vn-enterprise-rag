package com.hieulc.insightragingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentChunk(
    @JsonProperty("chunk_id") String chunkId,
    String text,
    ChunkMetadata metadata,
    @JsonProperty("chunk_index") int chunkIndex,
    float[] embedding) {
  public record ChunkMetadata(
      @JsonProperty("document_id") String documentId,
      @JsonProperty("page_number") int pageNumber) {}
}
