package com.hieulc.insightragretrieval.exception.infras;

import com.hieulc.coreinfrastructure.exception.InfrastructureException;
import lombok.Getter;

public class EmbeddingClientException extends InfrastructureException {
  @Getter private final String errorType;
  @Getter private final int statusCode;

  public EmbeddingClientException(String message, String errorType, int statusCode) {
    super(message);
    this.errorType = errorType;
    this.statusCode = statusCode;
  }
}
