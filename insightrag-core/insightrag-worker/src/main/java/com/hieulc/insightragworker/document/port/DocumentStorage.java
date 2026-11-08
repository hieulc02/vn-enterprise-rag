package com.hieulc.insightragworker.document.port;

public interface DocumentStorage {
  String calculateSha256(String bucket, String objectKey);
}
