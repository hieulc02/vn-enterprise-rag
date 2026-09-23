package com.hieulc.insightragretrieval.exception.appli;

public abstract class ApplicationException extends RuntimeException {
  protected ApplicationException(String message) {
    super(message);
  }
}
