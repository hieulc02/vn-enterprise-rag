package com.hieulc.insightragworker.document.command;

public record DocumentUploadCommand(
    String fileKey,
    String sequenceId,
    String eTag,
    String bucketName,
    String contentType,
    long sizeBytes) {
  public DocumentUploadCommand {
    if (fileKey == null || fileKey.isBlank()) {
      throw new IllegalArgumentException("File key is required");
    }
  }
}
