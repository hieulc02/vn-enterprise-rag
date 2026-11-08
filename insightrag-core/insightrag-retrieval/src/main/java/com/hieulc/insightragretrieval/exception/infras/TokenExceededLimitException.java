package com.hieulc.insightragretrieval.exception.infras;

import com.hieulc.coreinfrastructure.exception.InfrastructureException;

public class TokenExceededLimitException extends InfrastructureException {
  public TokenExceededLimitException(String message) {
    super(message);
  }
}
