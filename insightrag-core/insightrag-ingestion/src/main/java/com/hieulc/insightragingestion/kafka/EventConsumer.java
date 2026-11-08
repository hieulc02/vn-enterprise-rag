package com.hieulc.insightragingestion.kafka;

import com.hieulc.insightragingestion.dto.graph.DocumentGraph;
import com.hieulc.insightragingestion.dto.kafka.EventMessage;
import com.hieulc.insightragingestion.dto.kafka.EventPayload;
import com.hieulc.insightragingestion.service.graph.GraphIngestionService;
import com.hieulc.insightragingestion.service.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
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

    log.info(
        "Received event {} from topic: {}, partition: {}, offset {}",
        message.eventType(),
        topic,
        partition,
        offset);

    switch (message.eventType()) {
      case DELETED -> processDeletedEvent(message.correlationId());
      case COMPLETED -> processCompletedEvent(message);
      default ->
          log.warn(
              "Ignoring unsupported event type {} for key: {}", message.eventType(), message.id());
    }
  }

  private void processCompletedEvent(EventMessage message) {
    EventPayload payload = message.payload();
    try {
      DocumentGraph graph = convertPayload(payload);
      ingestionService.ingest(message.correlationId(), graph);
    } catch (JacksonException e) {
      // Fatal error
      String error =
          String.format("Failed to process graph ingestion for document: %s", payload.fileKey());
      log.error(error, e);
      throw new IllegalArgumentException(error, e);
    } catch (IOException e) {
      // Transient error
      log.warn("Network error downloading document: {}", payload.fileKey(), e);
      throw new UncheckedIOException(e);
    }
  }

  private void processDeletedEvent(String documentId) {
    if (documentId == null || documentId.isBlank()) {
      throw new IllegalArgumentException("Received DELETED event with null or empty documentId");
    }

    ingestionService.deleteDocumentGraph(documentId);
  }

  private DocumentGraph convertPayload(EventPayload payload) throws IOException, JacksonException {
    // avoid S3 stream resource leaks with try-with-resources
    try (InputStream stream = storageService.download(payload.bucketName(), payload.fileKey())) {
      return objectMapper.readValue(stream, DocumentGraph.class);
    }
  }
}
