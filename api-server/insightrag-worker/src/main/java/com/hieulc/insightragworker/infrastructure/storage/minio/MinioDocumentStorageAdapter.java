package com.hieulc.insightragworker.infrastructure.storage.minio;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

import com.hieulc.coreinfrastructure.exception.StorageProviderException;
import com.hieulc.insightragworker.document.port.DocumentStorage;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class MinioDocumentStorageAdapter implements DocumentStorage {

  private final MinioClient minioClient;

  @Override
  public String calculateSha256(String bucket, String objectKey) {
    try (InputStream stream =
        minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
      return sha256Hex(stream);
    } catch (MinioException | IOException e) {
      throw new StorageProviderException(
          "Failed to stream and calculate hash for file: " + objectKey, e);
    }
  }
}
