package com.hieulc.insightragretrieval.exception.infras;

import com.hieulc.coreinfrastructure.exception.InfrastructureException;

public class AiContentGenerateException extends InfrastructureException {
  public AiContentGenerateException(String message, Throwable cause) {
    super(message, cause);
  }
}
