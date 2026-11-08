package com.hieulc.insightragworker.document.command;

public record DocumentDeleteCommand(
    String fileKey, String sequenceId, String bucketName, String contentType) {
  public DocumentDeleteCommand {
    if (fileKey == null || fileKey.isBlank()) {
      throw new IllegalArgumentException("File key is required");
    }
  }
}
