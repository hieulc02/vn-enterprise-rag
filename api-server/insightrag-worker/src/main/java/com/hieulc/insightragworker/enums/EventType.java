package com.hieulc.insightragworker.enums;

public enum EventType {
    DOCUMENT_NEW,
    DOCUMENT_UPDATED;

    public static EventType isNewOrUpdated(boolean isNewDocument){
        return isNewDocument ? DOCUMENT_NEW : DOCUMENT_UPDATED;
    }
}
