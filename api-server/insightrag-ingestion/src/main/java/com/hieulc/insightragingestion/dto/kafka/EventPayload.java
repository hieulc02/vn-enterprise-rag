package com.hieulc.insightragingestion.dto.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EventPayload(
    @JsonProperty("file_key") String fileKey,
    @JsonProperty("bucket_name") String bucketName,
    @JsonProperty("content_type") String contentType) {}
