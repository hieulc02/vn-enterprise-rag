package com.hieulc.insightragworker.kafka.producer;

import com.hieulc.insightragworker.config.properties.KafkaTopicsProperties;
import com.hieulc.insightragworker.dto.OutboxEvent;
import com.hieulc.insightragworker.dto.PublishResult;
import com.hieulc.insightragworker.enums.AggregateType;
import com.hieulc.insightragworker.enums.EventType;
import com.hieulc.insightragworker.port.OutboxEventPublisher;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    private OutboxEventPublisher publisher;
    private KafkaTopicsProperties kafkaTopicsProperties;

    @BeforeEach
    void setUp(){

        kafkaTopicsProperties = new KafkaTopicsProperties(
                "rag.cdc.topic",
                "rag.dlq.topic",
                "rag.debezium.offset.topic"
        );

        publisher = new KafkaOutboxPublisher(objectMapper, kafkaTemplate, kafkaTopicsProperties);
    }

    @Test
    @DisplayName("Verify if the event payload and offSet sent to Kafka is correct and it only sends one time")
    void shouldPublishToKafkaOneTime_WithValidJsonPayload_AndReturnCorrectOffset(){

        String eventId = "26e6779f-f108-4a1c-831d-f1c8de56afa5";
        String aggregateId = "12345678-1234-5678-1234-567812345678";
        String data = "{\"fileKey\": \"itinerary.pdf\", \"bucketName\": \"documents\"}";

        String expectedJson = """
                {
                  "id": "26e6779f-f108-4a1c-831d-f1c8de56afa5",
                  "aggregate_type": "DOCUMENT",
                  "event_type": "DOCUMENT_NEW",
                  "payload": {
                    "fileKey": "itinerary.pdf",
                    "bucketName": "documents"
                  }
                }
                """;


        SendResult<String, String> mockSendResult = mock(SendResult.class);
        RecordMetadata mockRecordMetadata = mock(RecordMetadata.class);

        when(mockSendResult.getRecordMetadata()).thenReturn(mockRecordMetadata);
        when(mockRecordMetadata.offset()).thenReturn(100L);

        //stubbing the mock
       CompletableFuture<SendResult<String, String>> dummyFuture = CompletableFuture.completedFuture(mockSendResult);
       when(kafkaTemplate.send(eq(kafkaTopicsProperties.outboxEvent()), eq(aggregateId), anyString()))
               .thenReturn(dummyFuture);

       CompletableFuture<PublishResult> completableFuture = publisher.publish(createOutboxEvent(eventId, aggregateId, data));
       PublishResult publishResult = completableFuture.join();

       verify(kafkaTemplate).send(eq(kafkaTopicsProperties.outboxEvent()), eq(aggregateId), messageCaptor.capture());

       String jsonPayload = messageCaptor.getValue();

       JsonNode actualNode = objectMapper.readTree(jsonPayload);
       JsonNode expectedNode = objectMapper.readTree(expectedJson);

       assertThat(actualNode).isEqualTo(expectedNode);
       assertThat(publishResult.messageId()).isEqualTo("100");

    }

    @Test
    @DisplayName("Verify if the Kafka publish return the CompletableFuture synchronously if an unexpected Exception happens")
    void shouldReturnCompletableFutureSynchronously_whenExceptionIsThrown(){

        String eventId = "36e6779f-f108-4a1c-831d-f1c8de56afa5";
        String aggregateId = "22345678-1234-5678-1234-567812345678";
        String data = "{\"fileKey\": \"test2.pdf\", \"bucketName\": \"documents\"}";

        String exceptionMessage = "Unexpected Exception happens. Maybe Kafka is down??";

        when(kafkaTemplate.send(any(),any(), any()))
                .thenThrow(new RuntimeException(exceptionMessage));

        CompletableFuture<PublishResult> result = publisher.publish(createOutboxEvent(eventId, aggregateId, data));

        assertThat(result).isCompletedExceptionally()
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .withCauseInstanceOf(RuntimeException.class)
                .withMessageContaining(exceptionMessage);

    }

    @Test
    @DisplayName("Verify if the Kafka publish return the CompletableFuture asynchronously if an unexpected Exception happens")
    void shouldReturnCompletableFutureAsynchronously_whenExceptionIsThrown(){

        String eventId = "36e6779f-f108-4a1c-831d-f1c8de56afa5";
        String aggregateId = "22345678-1234-5678-1234-567812345678";
        String data = "{\"fileKey\": \"test2.pdf\", \"bucketName\": \"documents\"}";

        String exceptionMessage = "Kafka broker connection has been dropped!";

        when(kafkaTemplate.send(any(),any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException(exceptionMessage)));

        CompletableFuture<PublishResult> result =
                publisher.publish(createOutboxEvent(eventId, aggregateId, data));

        assertThat(result).isCompletedExceptionally()
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .withCauseInstanceOf(RuntimeException.class)
                .withMessageContaining(exceptionMessage);
    }

    private OutboxEvent createOutboxEvent(String eventId, String aggregateId, String data){
        return new OutboxEvent(
                        eventId,
                        AggregateType.DOCUMENT.toString(),
                        aggregateId,
                        EventType.DOCUMENT_NEW.toString(),
                        data);
    }

}