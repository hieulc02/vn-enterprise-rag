package com.hieulc.insightragingestion.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

public record EventPayload(
        @JsonProperty("file_key") String fileKey,
        @JsonProperty("bucket_name") String bucketName,
        String extension
) {
}
