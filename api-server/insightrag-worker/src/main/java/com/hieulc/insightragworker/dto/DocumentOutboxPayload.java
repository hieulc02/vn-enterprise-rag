package com.hieulc.insightragworker.dto;

public record DocumentOutboxPayload(
        String fileKey,
        String bucketName
) {
}
