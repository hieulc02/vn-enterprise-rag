package com.hieulc.insightragworker.infrastructure.storage.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hieulc.coreinfrastructure.exception.StorageProviderException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinioDocumentStorageAdapterTest {

  @Mock private MinioClient minioClient;

  @InjectMocks private MinioDocumentStorageAdapter adapter;

  @Test
  void calculateSha256_calculates_sha256_successfully_from_minio_stream() throws MinioException {
    String mockFileContent = "data stream from minio";
    InputStream mockStream =
        new ByteArrayInputStream(mockFileContent.getBytes(StandardCharsets.UTF_8));

    GetObjectResponse mockResponse = mock(GetObjectResponse.class, delegatesTo(mockStream));

    String expectedHash = DigestUtils.sha256Hex(mockFileContent);
    when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

    String actualHash = adapter.calculateSha256("bucket", "document.pdf");

    assertThat(actualHash).isEqualTo(expectedHash);

    ArgumentCaptor<GetObjectArgs> argsCaptor = ArgumentCaptor.forClass(GetObjectArgs.class);
    verify(minioClient).getObject(argsCaptor.capture());
    assertThat(argsCaptor.getValue().bucket()).isEqualTo("bucket");
    assertThat(argsCaptor.getValue().object()).isEqualTo("document.pdf");
  }

  @Test
  void calculateSha256_throws_MinioException_when_minio_client_fails() throws Exception {
    MinioException rootCause = new MinioException("Connection reset by peer");
    when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(rootCause);

    assertThatThrownBy(() -> adapter.calculateSha256("bucket", "document.pdf"))
        .isInstanceOf(StorageProviderException.class)
        .hasMessageContaining("Failed to stream and calculate hash for file: document.pdf")
        .hasCause(rootCause);
  }
}
