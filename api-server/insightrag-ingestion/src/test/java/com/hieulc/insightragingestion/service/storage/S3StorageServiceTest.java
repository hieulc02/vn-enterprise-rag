package com.hieulc.insightragingestion.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hieulc.coreinfrastructure.exception.StorageProviderException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

  @Mock private MinioClient minioClient;

  @InjectMocks private S3StorageService s3StorageService;

  @Test
  void download_return_InputStream_successfully() throws Exception {
    String expectedBucket = "bucket";
    String expectedKey = "test.pdf";

    GetObjectResponse mockResponse = mock(GetObjectResponse.class);
    when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);
    var response = s3StorageService.download(expectedBucket, expectedKey);

    assertThat(response).isSameAs(mockResponse);

    ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
    verify(minioClient).getObject(captor.capture());

    assertThat(captor.getValue().bucket()).isEqualTo(expectedBucket);
    assertThat(captor.getValue().object()).isEqualTo(expectedKey);
  }

  @Test
  void download_throws_StorageProviderException_when_s3_fails() {
    String bucket = "bucket";
    String key = "test.pdf";
    String originalErrorMessage = "Object does not exist";
    Throwable originalCause = new RuntimeException(originalErrorMessage);
    when(s3StorageService.download(bucket, key)).thenThrow(originalCause);

    assertThatThrownBy(() -> s3StorageService.download(bucket, key))
        .isInstanceOf(StorageProviderException.class)
        .hasMessageContaining(originalErrorMessage + " S3 failed process for file: " + key)
        .hasCause(originalCause);
  }
}
