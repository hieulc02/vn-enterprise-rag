package com.hieulc.insightragretrieval.dto.embedding;

import java.util.List;

public record EmbeddingResponse(List<EmbeddingData> data) {
  public record EmbeddingData(float[] embedding, int index) {}
}
