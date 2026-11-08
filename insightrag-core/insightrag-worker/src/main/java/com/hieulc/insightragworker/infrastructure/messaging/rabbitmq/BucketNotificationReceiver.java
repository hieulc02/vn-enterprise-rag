package com.hieulc.insightragworker.infrastructure.messaging.rabbitmq;

import com.hieulc.insightragworker.common.command.CommandHandler;
import com.hieulc.insightragworker.config.*;
import com.hieulc.insightragworker.document.command.DocumentDeleteCommand;
import com.hieulc.insightragworker.document.command.DocumentUploadCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class BucketNotificationReceiver {

  private final CommandHandler<DocumentUploadCommand> documentUploadHandler;
  private final CommandHandler<DocumentDeleteCommand> documentDeleteHandler;

  /**
   * Callback method receiving notification from minio
   *
   * @param payload convert payload event from S3 webhook
   */
  @RabbitListener(queues = "#{mainQueue.name}", containerFactory = "rabbitListenerContainerFactory")
  public void notificationHandler(@Payload S3EventPayload payload) {
    log.info(
        "Received MinIO event {} for document {}",
        payload.getRootEventName(),
        payload.getObjectKey());

    for (S3EventPayload.Record record : payload.getRecords()) {
      switch (record.getEventName()) {
        case DOCUMENT_UPLOADED -> processUploadEvent(payload);
        case DOCUMENT_DELETED -> processDeleteEvent(payload);
        case UNSUPPORTED_EVENT ->
            log.warn("Ignoring unsupported S3 storage event for key: {}", payload.getObjectKey());
      }
    }
  }

  private void processUploadEvent(S3EventPayload payload) {
    documentUploadHandler.handle(
        new DocumentUploadCommand(
            payload.getObjectKey(),
            payload.getSequencer(),
            payload.getETag(),
            payload.getBucketName(),
            payload.getContentType(),
            payload.getSize()));
  }

  private void processDeleteEvent(S3EventPayload payload) {
    documentDeleteHandler.handle(
        new DocumentDeleteCommand(
            payload.getObjectKey(),
            payload.getSequencer(),
            payload.getBucketName(),
            resolveContentType(payload)));
  }

  private String resolveContentType(S3EventPayload payload) {
    String rawContentType = payload.getContentType();
    if (rawContentType != null && !rawContentType.isBlank()) {
      return rawContentType;
    }

    String fileKey = payload.getObjectKey();
    if (fileKey != null) {
      return MediaTypeFactory.getMediaType(fileKey)
          .map(MediaType::toString)
          .orElse("application/octet-stream");
    }

    return "application/octet-stream";
  }
}
