package com.hieulc.coreinfrastructure.exception;

public abstract class InfrastructureException extends RuntimeException {
  protected InfrastructureException(String message) {
    super(message);
  }

  protected InfrastructureException(String message, Throwable cause) {
    super(message, cause);
  }
}
