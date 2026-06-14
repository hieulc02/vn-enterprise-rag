package com.hieulc.insightragworker.rabbitmq.listener;

import com.hieulc.insightragworker.command.DocumentUploadCommand;
import com.hieulc.insightragworker.command.handler.DocumentUploadHandler;
import com.hieulc.insightragworker.config.RabbitMQConfig;
import com.hieulc.insightragworker.config.properties.DocumentValidationProperties;
import com.hieulc.insightragworker.dto.S3EventPayload;
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



@Component
@Slf4j
@RequiredArgsConstructor
public class BucketNotificationReceiver {

    private final DocumentUploadHandler documentUploadHandler;
    private final DocumentValidationService documentValidationService;
    private final DocumentHashingService documentHashingService;
    private final DocumentValidationProperties documentValidationProperties;

    /**
     * Callback method receiving notification from minio
     *
     * @param payload convert payload event from S3 webhook
     */
    @RabbitListener(queues = RabbitMQConfig.MAIN_QUEUE, ackMode = "MANUAL")
    public void notificationHandler(
            @Payload S3EventPayload payload,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) {

        log.info("Received MinIO event for document {}", payload.getObjectKey());
        try {
            if (payload.isUploadEvent()) {

                String contentType = payload.getContentType();
                double documentSize = (double) payload.getSize() / DataSize.ofMegabytes(1).toBytes();
                if (!documentValidationService.isValidDocument(contentType, documentSize)) {
                    log.warn("Document file {} is invalid due to its content type {} or size {} MB", payload.getObjectKey(), contentType, documentSize);
                    channel.basicAck(tag, false);
                    return;
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

            channel.basicAck(tag, false);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
