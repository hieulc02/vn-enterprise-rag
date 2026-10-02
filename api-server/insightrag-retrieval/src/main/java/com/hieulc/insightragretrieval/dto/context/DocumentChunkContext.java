package com.hieulc.insightragretrieval.dto.context;

public record DocumentChunkContext(String chunkId, String text, ChunkMetadata metadata) {
  public record ChunkMetadata(String documentId, Integer pageNumber) {}
}
