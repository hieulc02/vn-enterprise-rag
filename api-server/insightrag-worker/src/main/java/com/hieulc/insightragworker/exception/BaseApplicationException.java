package com.hieulc.insightragworker.exception;

public abstract class BaseApplicationException extends RuntimeException {
    protected BaseApplicationException(String message){
        super(message);
    }

    protected BaseApplicationException(String message, Throwable cause){
        super(message, cause);
    }
}
