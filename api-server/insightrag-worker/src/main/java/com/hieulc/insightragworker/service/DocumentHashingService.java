package com.hieulc.insightragworker.service;

import com.hieulc.insightragworker.exception.infra.StorageProviderException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

@Service
@RequiredArgsConstructor
public class DocumentHashingService {

    private final MinioClient minioClient;

    public String calculateSha256(String bucket, String objectKey) {
        try(InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build()
        )){
            return sha256Hex(stream);
        } catch (MinioException | IOException e){
            throw new StorageProviderException("Failed to stream and calculate hash for file: " + objectKey, e);
        }
    }
}
