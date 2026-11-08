package com.hieulc.insightragingestion.service.storage;

import com.hieulc.coreinfrastructure.exception.StorageProviderException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService implements StorageService {

  private final MinioClient minioClient;

  @Override
  public InputStream download(String bucket, String key) {

    try {
      return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
    } catch (Exception e) {
      throw new StorageProviderException(e.getMessage() + " S3 failed process for file: " + key, e);
    }
  }
}
