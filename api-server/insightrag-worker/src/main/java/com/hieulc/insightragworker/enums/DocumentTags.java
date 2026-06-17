package com.hieulc.insightragworker.enums;

import lombok.Getter;

@Getter
public enum DocumentTags {

    DEPARTMENTS("DEPARTMENTS"),
    ACL_ROLES("ACL");

    private final String tag;

    DocumentTags(String tag){
        this.tag = tag;
    }
}
