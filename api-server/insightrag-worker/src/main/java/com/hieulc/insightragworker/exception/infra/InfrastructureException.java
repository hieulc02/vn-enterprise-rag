package com.hieulc.insightragworker.exception.infra;

import com.hieulc.insightragworker.exception.BaseApplicationException;

public abstract class InfrastructureException extends BaseApplicationException {
    protected InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
