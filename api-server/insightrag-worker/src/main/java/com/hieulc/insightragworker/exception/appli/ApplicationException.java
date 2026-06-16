package com.hieulc.insightragworker.exception.appli;

import com.hieulc.insightragworker.exception.BaseApplicationException;

public abstract class ApplicationException extends BaseApplicationException {
    protected ApplicationException(String message) {
        super(message);
    }
}
