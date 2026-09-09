package com.hieulc.insightragingestion.exception;

import com.hieulc.coreinfrastructure.exception.InfrastructureException;

public class GraphIngestionException extends InfrastructureException {
  public GraphIngestionException(String message, Throwable cause) {
    super(message, cause);
  }
}
