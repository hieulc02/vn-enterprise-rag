package com.hieulc.insightragworker.infrastructure.messaging.rabbitmq;

import static com.hieulc.insightragworker.helper.S3EventPayloadDataTest.createS3EventPayload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.hieulc.coreinfrastructure.exception.StorageProviderException;
import com.hieulc.insightragworker.base.AbstractRabbitMqIT;
import com.hieulc.insightragworker.common.command.CommandHandler;
import com.hieulc.insightragworker.config.RabbitMqConfig;
import com.hieulc.insightragworker.config.properties.RabbitMqProperties;
import com.hieulc.insightragworker.document.command.DocumentDeleteCommand;
import com.hieulc.insightragworker.document.command.DocumentUploadCommand;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {
      RabbitMqConfig.class,
      BucketNotificationReceiver.class,
    })
@ActiveProfiles("test")
@ImportAutoConfiguration(RabbitAutoConfiguration.class)
@EnableConfigurationProperties(RabbitMqProperties.class)
class RabbitMqIntegrationTest extends AbstractRabbitMqIT {
  @MockitoBean private CommandHandler<DocumentUploadCommand> uploadHandler;
  @MockitoBean private CommandHandler<DocumentDeleteCommand> deleteHandler;

  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private RabbitMqProperties rabbitMqProperties;

  @Test
  void should_trigger_jackson_convert_when_receiving_raw_upload_message() {
    String payload =
        """
        {
          "EventName": "s3:ObjectCreated:Put",
          "Key": "uploads/document.pdf",
          "Records": [
            {
              "eventName": "s3:ObjectCreated:Put",
              "s3": {
                "bucket": {
                  "name": "uploads"
                },
                "object": {
                  "key": "uploads/document.pdf",
                  "size": 1048576,
                  "eTag": "1b2cf535f27731c974343645a3985328",
                  "contentType": "application/pdf",
                  "sequencer": "0055AED6DCD891EFE2"
                }
              }
            }
          ]
        }
        """;

    Message message =
        MessageBuilder.withBody(payload.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    rabbitTemplate.convertAndSend(
        rabbitMqProperties.mainExchange(), rabbitMqProperties.mainRoutingKey(), message);

    ArgumentCaptor<DocumentUploadCommand> requestCaptor =
        ArgumentCaptor.forClass(DocumentUploadCommand.class);
    await()
        .atMost(Duration.ofSeconds(2L))
        .untilAsserted(
            () -> {
              verify(uploadHandler, times(1)).handle(requestCaptor.capture());
              DocumentUploadCommand messagePayload = requestCaptor.getValue();
              assertThat(messagePayload).isNotNull();
              assertThat(messagePayload.fileKey()).isEqualTo("uploads/document.pdf");
              assertThat(messagePayload.eTag()).isEqualTo("1b2cf535f27731c974343645a3985328");
              assertThat(messagePayload.bucketName()).isEqualTo("uploads");
              assertThat(messagePayload.contentType()).isEqualTo("application/pdf");
              assertThat(messagePayload.sizeBytes()).isEqualTo(1048576L);
            });
  }

  @Test
  void should_trigger_jackson_convert_when_receiving_raw_delete_message() {
    String payload =
        """
            {
              "EventName": "s3:ObjectRemoved:Delete",
              "Key": "uploads/deleted-document.pdf",
              "Records": [
                {
                  "eventName": "s3:ObjectRemoved:Delete",
                  "s3": {
                    "bucket": {
                      "name": "uploads"
                    },
                    "object": {
                      "key": "uploads/deleted-document.pdf",
                      "sequencer": "0055AED6DCD891EFE2"
                    }
                  }
                }
              ]
            }
            """;

    Message message =
        MessageBuilder.withBody(payload.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    rabbitTemplate.convertAndSend(
        rabbitMqProperties.mainExchange(), rabbitMqProperties.mainRoutingKey(), message);

    ArgumentCaptor<DocumentDeleteCommand> requestCaptor =
        ArgumentCaptor.forClass(DocumentDeleteCommand.class);
    await()
        .atMost(Duration.ofSeconds(2L))
        .untilAsserted(
            () -> {
              verify(deleteHandler, times(1)).handle(requestCaptor.capture());
              DocumentDeleteCommand messagePayload = requestCaptor.getValue();
              assertThat(messagePayload).isNotNull();
              assertThat(messagePayload.fileKey()).isEqualTo("uploads/deleted-document.pdf");
              assertThat(messagePayload.bucketName()).isEqualTo("uploads");
              assertThat(messagePayload.contentType()).isEqualTo("application/pdf");
            });
  }

  @Test
  void should_retry_on_StorageProviderException_and_move_to_Dql() {
    S3EventPayload payload =
        createS3EventPayload(
            S3EventName.DOCUMENT_UPLOADED, "uploads", "document.pdf", 10L, "application/pdf", 1);

    Throwable throwable = new Exception("Connection time out");
    doThrow(new StorageProviderException("Minio down", throwable))
        .when(uploadHandler)
        .handle(any(DocumentUploadCommand.class));

    rabbitTemplate.convertAndSend(
        rabbitMqProperties.mainExchange(), rabbitMqProperties.mainRoutingKey(), payload);

    await()
        .atMost(Duration.ofSeconds(15L))
        .untilAsserted(
            () -> {
              // Verify 1 initial try + 3 retries
              verify(uploadHandler, times(4)).handle(any());
            });

    Message dlqMessage = rabbitTemplate.receive(rabbitMqProperties.dlQueue());
    assertThat(dlqMessage).isNotNull();

    Map<String, Object> headers = dlqMessage.getMessageProperties().getHeaders();

    assertThat(headers).containsKey("x-exception-message");
    assertThat(headers.get("x-exception-message").toString()).contains("Minio down");

    assertThat(headers).containsKey("x-exception-stacktrace");
    assertThat(headers.get("x-exception-stacktrace").toString())
        .contains("Connection time out")
        .contains("StorageProviderException");

    assertThat(headers).containsKey("x-original-exchange");
    assertThat(headers).containsKey("x-original-routingKey");

    String dlBody = new String(dlqMessage.getBody());
    assertThat(dlBody).contains("document.pdf");
  }

  @Test
  void should_not_retry_on_IllegalArgumentException_and_move_to_Dlq() {
    S3EventPayload payload =
        createS3EventPayload(
            S3EventName.DOCUMENT_UPLOADED,
            "uploads",
            "document.docx",
            10L,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            1);

    doThrow(new IllegalArgumentException("Invalid document file"))
        .when(uploadHandler)
        .handle(any(DocumentUploadCommand.class));

    rabbitTemplate.convertAndSend(
        rabbitMqProperties.mainExchange(), rabbitMqProperties.mainRoutingKey(), payload);

    await()
        .atMost(Duration.ofSeconds(2L))
        .untilAsserted(() -> verify(uploadHandler, times(1)).handle(any()));

    Message dlqMessage = rabbitTemplate.receive(rabbitMqProperties.dlQueue());
    assertThat(dlqMessage).isNotNull();

    Map<String, Object> headers = dlqMessage.getMessageProperties().getHeaders();

    assertThat(headers).containsKey("x-exception-message");
    assertThat(headers.get("x-exception-message").toString()).contains("Invalid document file");

    String dlBody = new String(dlqMessage.getBody());
    assertThat(dlBody).contains("document.docx");
  }

  @Test
  void should_not_retry_on_generic_exception_and_move_to_Dql() {
    S3EventPayload payload =
        createS3EventPayload(
            S3EventName.DOCUMENT_UPLOADED, "uploads", "document.pdf", 10L, "application/pdf", 1);

    doThrow(new RuntimeException("Unknown exception"))
        .when(uploadHandler)
        .handle(any(DocumentUploadCommand.class));

    rabbitTemplate.convertAndSend(
        rabbitMqProperties.mainExchange(), rabbitMqProperties.mainRoutingKey(), payload);

    await()
        .atMost(Duration.ofSeconds(2L))
        .untilAsserted(() -> verify(uploadHandler, times(1)).handle(any()));

    Message dlqMessage = rabbitTemplate.receive(rabbitMqProperties.dlQueue());
    assertThat(dlqMessage).isNotNull();

    Map<String, Object> headers = dlqMessage.getMessageProperties().getHeaders();

    assertThat(headers).containsKey("x-exception-message");
    assertThat(headers.get("x-exception-message").toString()).contains("Unknown exception");

    String dlBody = new String(dlqMessage.getBody());
    assertThat(dlBody).contains("document.pdf");
  }
}
