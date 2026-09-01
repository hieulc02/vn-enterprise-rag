package com.hieulc.insightragworker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentOutboxPayload(
        @JsonProperty("file_key") String fileKey,
        @JsonProperty("bucket_name") String bucketName,
        @JsonProperty("extension") String extension
) {
}
