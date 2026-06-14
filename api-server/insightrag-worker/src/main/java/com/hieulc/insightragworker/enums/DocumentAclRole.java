package com.hieulc.insightragworker.enums;

import com.rabbitmq.client.Return;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum DocumentAclRole {
    PUBLIC,
    PROTECTED,
    PRIVATE;

    public static DocumentAclRole fromBucketName(String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            log.error("Bucket name is null or empty. Set ACL default to PRIVATE");
            return PRIVATE;
        }

        int underscorePriorIndex = bucketName.indexOf('_');
        if (underscorePriorIndex == -1) {
            log.warn("Non-compliant bucket name format (missing underscore): {}. Set ACL default to PRIVATE", bucketName);
            return PRIVATE;
        }

        String aclRolePrefix = bucketName.substring(0, underscorePriorIndex).toLowerCase().trim();

        try {
            return DocumentAclRole.valueOf(aclRolePrefix);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown ACL prefix '{}' extracted from bucket '{}'. Set ACL default to PRIVATE", aclRolePrefix, bucketName);
            return PRIVATE;
        }
    }
}
