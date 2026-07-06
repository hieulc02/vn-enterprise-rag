package com.hieulc.insightragworker.kafka.producer;

import com.hieulc.insightragworker.config.KafkaConfig;
import com.hieulc.insightragworker.dto.OutboxEvent;
import com.hieulc.insightragworker.enums.AggregateType;
import com.hieulc.insightragworker.enums.EventType;
import com.hieulc.insightragworker.port.OutboxEventPublisher;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Testcontainers
class KafkaOutboxPublisherTestIT {

    @Container
    final static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.0"));

    final private ObjectMapper objectMapper = new ObjectMapper();
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxEventPublisher outboxEventPublisher;

    @BeforeEach
    void setup() throws InterruptedException, ExecutionException {
        String broker = kafka.getBootstrapServers();

        try (AdminClient adminClient = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker))) {
            adminClient.createTopics(Collections.singletonList(
                            new NewTopic(KafkaConfig.OUTBOX_TOPIC, 3, (short) 1)
                    )).all().get();
        }

        DefaultKafkaProducerFactory<String, String> producer = createProducer(broker);

        kafkaTemplate = new KafkaTemplate<>(producer, true);

        outboxEventPublisher = new KafkaOutboxPublisher(objectMapper, kafkaTemplate);
    }

    @Test
    @DisplayName("Kafka should receive and send the valid Outbox Event Json")
    void shouldReceiveValidOutboxEventJson() {

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

        String brokerGroup = "cdc-test-group";
        String broker = kafka.getBootstrapServers();

        Consumer<String, String> consumer = createConsumer(brokerGroup, broker);
        consumer.subscribe(Collections.singletonList(KafkaConfig.OUTBOX_TOPIC));

        outboxEventPublisher.publish(createOutboxEvent(eventId, aggregateId, data));

        ConsumerRecord<String, String> receivedRecord = KafkaTestUtils.getSingleRecord(consumer, KafkaConfig.OUTBOX_TOPIC);

        JsonNode actualNode = objectMapper.readTree(receivedRecord.value());
        JsonNode expectedNode = objectMapper.readTree(expectedJson);

        assertThat(actualNode).isEqualTo(expectedNode);

        consumer.close();
    }

    private OutboxEvent createOutboxEvent(String eventId, String aggregateId, String data){
        return new OutboxEvent(
                eventId,
                AggregateType.DOCUMENT.toString(),
                aggregateId,
                EventType.DOCUMENT_NEW.toString(),
                data);
    }

    private Consumer<String, String> createConsumer(String brokerGroup, String broker){
        Map<String, Object> config = KafkaTestUtils.consumerProps(broker, brokerGroup, true);
        //KafkaTestUtils does not support the StringDeserializer consumer key so we must override it to suit our case
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        DefaultKafkaConsumerFactory<String, String> consumerConfig = new DefaultKafkaConsumerFactory<>(config);

        return consumerConfig.createConsumer();
    }

    private DefaultKafkaProducerFactory<String, String> createProducer(String broker){
        Map<String, Object> config = KafkaTestUtils.producerProps(broker);
        //KafkaTestUtils does not support the StringSerializer producer key so we must override it to suit our case
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

}