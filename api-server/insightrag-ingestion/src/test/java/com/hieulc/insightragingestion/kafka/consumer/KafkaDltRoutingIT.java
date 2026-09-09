package com.hieulc.insightragingestion.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.hieulc.insightragingestion.base.AbstractKafkaIT;
import com.hieulc.insightragingestion.config.KafkaTestConfig;
import com.hieulc.insightragingestion.dto.EventMessage;
import com.hieulc.insightragingestion.dto.EventPayload;
import com.hieulc.insightragingestion.service.graph.GraphIngestionService;
import com.hieulc.insightragingestion.service.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(KafkaTestConfig.class)
class KafkaDltRoutingIT extends AbstractKafkaIT {

  @Value("${insightrag.kafka.topics.consumer}")
  private String mainTopic;

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  @MockitoBean GraphIngestionService ingestionService;

  @MockitoBean StorageService storageService;

  @Test
  void shouldRouteToDltWhenRetriesExhausted() {
    String fileId = "test-file";
    String dltTopic = mainTopic + "-dlt";

    doReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)))
        .when(storageService)
        .download(anyString(), anyString());

    doThrow(new RuntimeException()).when(ingestionService).ingest(any());

    EventMessage message = dummyEventMessage(fileId);

    kafkaTemplate.send(mainTopic, message);

    try (Consumer<String, String> consumer = createTestConsumer()) {
      consumer.subscribe(Collections.singletonList(dltTopic));

      ConsumerRecord<String, String> record =
          KafkaTestUtils.getSingleRecord(consumer, dltTopic, Duration.ofSeconds(10));

      byte[] exceptionHeader = record.headers().lastHeader("kafka_dlt-exception-message").value();
      assertThat(new String(exceptionHeader))
          .contains("Failed to process graph ingestion for document: %s".formatted(fileId));
    }
  }

  private Consumer<String, String> createTestConsumer() {
    Map<String, Object> props =
        KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), "test-group", false);
    DefaultKafkaConsumerFactory<String, String> cf =
        new DefaultKafkaConsumerFactory<>(
            props, new StringDeserializer(), new StringDeserializer());

    return cf.createConsumer();
  }

  private EventMessage dummyEventMessage(String id) {
    return new EventMessage(
        "test-event",
        "DOCUMENT",
        "GRAPH-POISON",
        new EventPayload(id, "test-bucket", ".jsonl"),
        null,
        null);
  }
}
