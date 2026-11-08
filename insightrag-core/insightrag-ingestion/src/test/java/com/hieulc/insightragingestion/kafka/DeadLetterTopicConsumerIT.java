package com.hieulc.insightragingestion.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.hieulc.insightragingestion.base.AbstractKafkaIT;
import com.hieulc.insightragingestion.config.KafkaTestConfig;
import com.hieulc.insightragingestion.dto.kafka.EventMessage;
import com.hieulc.insightragingestion.dto.kafka.EventPayload;
import com.hieulc.insightragingestion.enums.EventType;
import com.hieulc.insightragingestion.service.audit.AuditDatabaseService;
import com.hieulc.insightragingestion.service.graph.GraphIngestionService;
import com.hieulc.insightragingestion.service.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(KafkaTestConfig.class)
class DeadLetterTopicConsumerIT extends AbstractKafkaIT {

  @Value("${insightrag.kafka.topics.consumer}")
  private String mainTopic;

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  @MockitoBean private AuditDatabaseService auditDatabaseService;

  @MockitoBean GraphIngestionService ingestionService;

  @MockitoBean StorageService storageService;

  @Test
  void consumeDlt_routes_to_dlt_when_retries_exhausted() {
    String fileId = "test-file";
    //    String dltTopic = mainTopic + "-dlt";

    doReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)))
        .when(storageService)
        .download(anyString(), anyString());
    doThrow(new RuntimeException()).when(ingestionService).ingest(anyString(), any());

    EventMessage message = dummyEventMessage(fileId);

    kafkaTemplate.send(mainTopic, message);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              ArgumentCaptor<String> exceptionCaptor = ArgumentCaptor.forClass(String.class);
              verify(auditDatabaseService, atLeastOnce())
                  .saveAuditRecord(any(), any(), exceptionCaptor.capture(), any(), any(), any());

              assertThat(exceptionCaptor.getAllValues())
                  .anyMatch(
                      msg ->
                          msg.contains(
                              "Failed to process graph ingestion for document: " + fileId));
            });

    // Problem: Producer Send before consumer subscription Race Condition (Secondary Flakiness)
    // The `kafkaTemplate.send(mainTopic, message)` is called before the test consumer is created
    // and subscribed. It only passes in isolation because `auto.offset.reset` is `earliest`. If we
    // change the offset strategy without reordering the setup, the test consumer might miss the DLT
    // message if retry exhaustion completes before the consumer joins the group.
    //    try (Consumer<String, String> consumer = createTestConsumer()) {
    //      consumer.subscribe(Collections.singletonList(dltTopic));
    //
    //      ConsumerRecord<String, String> record =
    //          KafkaTestUtils.getSingleRecord(consumer, dltTopic, Duration.ofSeconds(10));
    //
    //      byte[] exceptionHeader =
    // record.headers().lastHeader("kafka_dlt-exception-message").value();
    //      assertThat(new String(exceptionHeader))
    //          .contains("Failed to process graph ingestion for document: %s".formatted(fileId));
    //    }
  }

  @Test
  void consumeDlt_processes_raw_string_and_calls_database_service() throws Exception {
    String poisonPill = "poison_pill{{{";
    String key = "test-doc-id";
    String dltTopic = mainTopic + "-dlt";

    //  kafkaTemplate.send(dltTopic, key, poisonPill);
    // Kafka passes the payload into `JsonSerializer` which is part of main consumer configuration.
    // Therefore, Jackson serializes string into literal byte payload.
    // so when the DLT consumer using a `StringDeserializer` reads those byte, it reads exactly what
    // is on broker, including the extra double quotes Jackson added. This causes our assertion to
    // fail.

    // Use a native `StringSerializer` that matches the DLT to bypass the
    // KafkaTemplate's JSON configuration.
    try (Producer<String, String> producer = createTestProducer()) {
      var record = new ProducerRecord<>(dltTopic, key, poisonPill);
      producer.send(record); // wait for the broker acknowledgement
    }

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

              verify(auditDatabaseService)
                  .saveAuditRecord(eq(key), captor.capture(), any(), any(), any(), any());

              String payload = captor.getValue();
              assertThat(payload).isEqualTo(poisonPill);
            });
  }

  private Producer<String, String> createTestProducer() {
    Map<String, Object> producerProps = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
    return new KafkaProducer<>(producerProps, new StringSerializer(), new StringSerializer());
  }

  //  private Consumer<String, String> createTestConsumer() {
  //    Map<String, Object> props =
  //        KafkaTestUtils.consumerProps(
  //            kafka.getBootstrapServers(), UUID.randomUUID().toString(), false);
  //    DefaultKafkaConsumerFactory<String, String> cf =
  //        new DefaultKafkaConsumerFactory<>(
  //            props, new StringDeserializer(), new StringDeserializer());
  //
  //    return cf.createConsumer();
  //  }

  private EventMessage dummyEventMessage(String id) {
    return new EventMessage(
        "test-event",
        null,
        "doc-id",
        "DOCUMENT",
        EventType.COMPLETED,
        new EventPayload(id, "test-bucket", "application/json"));
  }
}
