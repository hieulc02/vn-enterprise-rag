package com.hieulc.insightragworker.common.exception;

public abstract class ApplicationException extends RuntimeException {
  protected ApplicationException(String message) {
    super(message);
  }
}
