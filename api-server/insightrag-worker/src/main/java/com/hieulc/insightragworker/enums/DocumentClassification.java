package com.hieulc.insightragworker.enums;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum DocumentClassification {
    CLASSIFIED, UNCLASSIFIED;

    public static DocumentClassification fromAclRole(DocumentAclRole documentAclRole){
       if(documentAclRole == null){
           log.warn("ACL Role is null. Set classification default to UNCLASSIFIED");
           return UNCLASSIFIED;
       }

       return switch (documentAclRole){
           case PUBLIC -> UNCLASSIFIED;
           case PRIVATE, PROTECTED -> CLASSIFIED;
       };
    }
}
