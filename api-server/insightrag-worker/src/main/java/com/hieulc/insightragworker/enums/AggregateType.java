package com.hieulc.insightragworker.enums;

public enum AggregateType {
    DOCUMENT("Document");

    final String type;

    AggregateType(String type){
        this.type = type;
    }
}
