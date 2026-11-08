package com.hieulc.insightragworker.infrastructure.messaging.rabbitmq;

import static com.hieulc.insightragworker.helper.S3EventPayloadDataTest.createS3EventPayload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hieulc.insightragworker.common.command.CommandHandler;
import com.hieulc.insightragworker.document.command.DocumentDeleteCommand;
import com.hieulc.insightragworker.document.command.DocumentUploadCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BucketNotificationReceiverTest {

  @Mock private CommandHandler<DocumentUploadCommand> documentUploadHandler;
  @Mock private CommandHandler<DocumentDeleteCommand> documentDeleteHandler;

  private BucketNotificationReceiver bucketNotificationReceiver;

  @BeforeEach
  void setUp() {
    bucketNotificationReceiver =
        new BucketNotificationReceiver(documentUploadHandler, documentDeleteHandler);
  }

  @Test
  void notificationHandler_routes_to_upload_document_when_document_upload_event_received() {
    S3EventPayload payload =
        createS3EventPayload(
            S3EventName.DOCUMENT_UPLOADED,
            "bucket",
            "document-upload.pdf",
            10L,
            "application/pdf",
            1);
    bucketNotificationReceiver.notificationHandler(payload);

    verify(documentUploadHandler, times(1)).handle(any());
    verifyNoInteractions(documentDeleteHandler);
  }

  @Test
  void notificationHandler_routes_to_delete_document_when_document_delete_event_received() {
    S3EventPayload payload =
        createS3EventPayload(
            S3EventName.DOCUMENT_DELETED, "bucket", "document-delete.pdf", null, null, 1);
    bucketNotificationReceiver.notificationHandler(payload);

    verify(documentDeleteHandler, times(1)).handle(any());
    verifyNoInteractions(documentUploadHandler);
  }

  @Test
  void notificationHandler_ignores_event_when_receiving_unsupported_event() {
    S3EventPayload payload =
        createS3EventPayload(
            S3EventName.TAGS_MODIFIED, "bucket", "document-ignore.pdf", 10L, "application/pdf", 1);
    bucketNotificationReceiver.notificationHandler(payload);

    verifyNoInteractions(documentDeleteHandler);
    verifyNoInteractions(documentUploadHandler);

    assertThatCode(() -> bucketNotificationReceiver.notificationHandler(payload))
        .doesNotThrowAnyException();
  }

  @Test
  void notificationHandler_processes_multiple_events_within_single_payload() {
    S3EventPayload payload =
        createS3EventPayload(
            S3EventName.DOCUMENT_UPLOADED,
            "bucket",
            "document-multiple.pdf",
            10L,
            "application/pdf",
            3);
    bucketNotificationReceiver.notificationHandler(payload);

    verify(documentUploadHandler, times(3)).handle(any());
    verifyNoInteractions(documentDeleteHandler);
  }

  @Test
  void notificationHandler_maps_and_parses_payload_to_command_correctly_on_delete_event() {
    S3EventPayload payload =
        createS3EventPayload(
            S3EventName.DOCUMENT_DELETED, "bucket", "document-test.pdf", null, null, 1);
    bucketNotificationReceiver.notificationHandler(payload);

    ArgumentCaptor<DocumentDeleteCommand> captor =
        ArgumentCaptor.forClass(DocumentDeleteCommand.class);
    verify(documentDeleteHandler).handle(captor.capture());
    DocumentDeleteCommand deleteCommand = captor.getValue();

    assertThat(deleteCommand.fileKey()).isEqualTo("document-test.pdf");
    assertThat(deleteCommand.bucketName()).isEqualTo("bucket");
    assertThat(deleteCommand.contentType()).isEqualTo("application/pdf");
  }
}
