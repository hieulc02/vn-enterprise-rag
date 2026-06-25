package com.hieulc.insightragworker.rabbitmq.listener;

import com.hieulc.insightragworker.command.*;
import com.hieulc.insightragworker.command.handler.*;
import com.hieulc.insightragworker.config.*;
import com.hieulc.insightragworker.config.properties.*;
import com.hieulc.insightragworker.dto.S3EventPayload;
import com.hieulc.insightragworker.exception.appli.DepartmentInvalidException;
import com.hieulc.insightragworker.exception.infra.StorageProviderException;
import com.hieulc.insightragworker.service.DocumentHashingService;
import com.hieulc.insightragworker.validation.DocumentValidationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.io.IOException;


@Component
@Slf4j
@RequiredArgsConstructor
public class BucketNotificationReceiver {

    private final DocumentUploadHandler documentUploadHandler;
    private final DocumentTaggingHandler documentTaggingHandler;

    private final DocumentValidationService documentValidationService;
    private final DocumentHashingService documentHashingService;
    private final DocumentValidationProperties documentValidationProperties;

    /**
     * Callback method receiving notification from minio
     *
     * @param payload convert payload event from S3 webhook
     */
    @RabbitListener(queues = RabbitMQConfig.MAIN_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void notificationHandler(@Payload S3EventPayload payload) {
        log.info("Received MinIO event {} for document {}", payload.getRootEventName(), payload.getObjectKey());

        for (S3EventPayload.Record record : payload.getRecords()) {
            switch (record.getEventName()) {
                case DOCUMENT_UPLOADED -> processUploadEvent(payload);
                case TAGS_MODIFIED -> processTaggingEvent(payload);
                case DOCUMENT_DELETED -> processDeleteEvent(payload);
                case UNSUPPORTED_EVENT ->
                        log.warn("Ignoring unsupported S3 storage event for key: {}", payload.getObjectKey());
            }
        }
    }

    private void processUploadEvent(S3EventPayload payload) {
        String contentType = payload.getContentType();
        double documentSize = (double) payload.getSize() / DataSize.ofMegabytes(1).toBytes();

        if (!documentValidationService.isValidDocument(contentType, documentSize)) {
            throw new IllegalArgumentException(
                    String.format("Invalid document file [%s] type [%s] or size [%.2f MB]", payload.getObjectKey(), contentType, documentSize)
            );
        }

        //https://github.com/minio/minio/discussions/17146
        //eTag threshold is less than 16 MB
        String hashContent;
        if (documentSize > documentValidationProperties.eTagThresholdMb()) {
            log.debug("Document {} exceeds eTag threshold. Calculating streaming hash.", payload.getObjectKey());
            hashContent = documentHashingService.calculateSha256(payload.getBucketName(), payload.getObjectKey());
        } else {
            hashContent = payload.getETag();
        }

        documentUploadHandler.handle(
                new DocumentUploadCommand(
                        payload.getObjectKey(),
                        payload.getSequencer(),
                        hashContent,
                        payload.getBucketName())
        );
    }

    private void processTaggingEvent(S3EventPayload payload) {
        log.debug("Routing document tagging update for key: {}", payload.getObjectKey());

        documentTaggingHandler.handle(
                new DocumentTaggingCommand(
                        payload.getObjectKey(),
                        payload.getBucketName()
                )
        );
    }

    private void processDeleteEvent(S3EventPayload payload) {

    }

}
