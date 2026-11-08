package com.hieulc.insightragworker.outbox.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentOutboxPayload(
    @JsonProperty("file_key") String fileKey,
    @JsonProperty("bucket_name") String bucketName,
    @JsonProperty("content_type") String contentType) {
  public static DocumentOutboxPayload of(String fileKey, String bucketName, String contentType) {
    return new DocumentOutboxPayload(fileKey, bucketName, contentType);
  }
}
