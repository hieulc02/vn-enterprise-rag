package com.hieulc.insightragretrieval.dto.context;

public record DocumentChunkContext(String chunkId, String text, ChunkMetadata metadata) {
  public record ChunkMetadata(String documentKey, Integer pageNumber) {}
}
