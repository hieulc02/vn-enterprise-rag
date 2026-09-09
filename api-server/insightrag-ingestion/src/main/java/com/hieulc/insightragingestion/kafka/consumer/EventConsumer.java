package com.hieulc.insightragingestion.kafka.consumer;

import com.hieulc.insightragingestion.dto.DocumentGraph;
import com.hieulc.insightragingestion.dto.EventMessage;
import com.hieulc.insightragingestion.dto.EventPayload;
import com.hieulc.insightragingestion.exception.GraphIngestionException;
import com.hieulc.insightragingestion.service.graph.GraphIngestionService;
import com.hieulc.insightragingestion.service.storage.StorageService;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventConsumer {

  private final StorageService storageService;
  private final GraphIngestionService ingestionService;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = "${insightrag.kafka.topics.consumer}")
  public void consume(
      EventMessage message,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_PARTITION) String partition,
      @Header(KafkaHeaders.OFFSET) long offset) {
    EventPayload payload = message.payload();
    log.info(
        "Received ingestion event from topic: {}, partition: {}, offset {} for bucket: {}, key: {}",
        topic,
        partition,
        offset,
        payload.bucketName(),
        payload.fileKey());
    try {
      DocumentGraph graph = convert_payload(payload);
      ingestionService.ingest(graph);
    } catch (Exception e) {
      String error =
          String.format("Failed to process graph ingestion for document: %s", payload.fileKey());
      log.error(error, e);
      throw new GraphIngestionException(error, e);
    }
  }

  private DocumentGraph convert_payload(EventPayload payload) throws Exception {
    // avoid S3 stream resource leaks with try-with-resources
    try (InputStream stream = storageService.download(payload.bucketName(), payload.fileKey())) {
      return objectMapper.readValue(stream, DocumentGraph.class);
    }
  }
}
