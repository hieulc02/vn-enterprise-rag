package com.hieulc.insightragretrieval.dto.embedding;

import java.util.List;

public record EmbeddingErrorResponse(List<ValidationError> detail) {
  public record ValidationError(List<Object> loc, String msg, String type) {}
}
