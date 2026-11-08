package com.hieulc.insightragingestion.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.hieulc.insightragingestion.dto.graph.DocumentGraph;
import com.hieulc.insightragingestion.dto.kafka.EventMessage;
import com.hieulc.insightragingestion.dto.kafka.EventPayload;
import com.hieulc.insightragingestion.enums.EventType;
import com.hieulc.insightragingestion.service.graph.GraphIngestionService;
import com.hieulc.insightragingestion.service.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

  @Mock private StorageService storageService;
  @Mock private GraphIngestionService ingestionService;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private EventConsumer consumer;

  @Test
  void consume_process_CompletedEvent_successfully() {
    var completedMessage =
        createEventMessage(EventType.COMPLETED, "graph-completed.json", "document-id");

    InputStream stream = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
    when(storageService.download(anyString(), anyString())).thenReturn(stream);

    DocumentGraph dummyGraph = mock(DocumentGraph.class);
    when(objectMapper.readValue(stream, DocumentGraph.class)).thenReturn(dummyGraph);

    consumer.consume(completedMessage, "topic", "0", 1L);

    verify(ingestionService).ingest("document-id", dummyGraph);
  }

  @Test
  void consume_CompletedEvent_throws_IllegalArgumentException_when_jackson_fails() {
    var completedMessage = createEventMessage(EventType.COMPLETED, "corrupt.json", "document-id");
    String invalidContent = "{ invalid json";
    InputStream stream = new ByteArrayInputStream(invalidContent.getBytes(StandardCharsets.UTF_8));
    when(storageService.download(anyString(), anyString())).thenReturn(stream);

    JacksonException jacksonException;
    try {
      new ObjectMapper().readTree(invalidContent);
      throw new IllegalStateException("Should have thrown an exception");
    } catch (JacksonException e) {
      jacksonException = e;
    }
    when(objectMapper.readValue(stream, DocumentGraph.class)).thenThrow(jacksonException);

    assertThatThrownBy(() -> consumer.consume(completedMessage, "topic", "0", 2L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to process graph ingestion for document: corrupt.json")
        .hasCause(jacksonException);

    verify(ingestionService, never()).ingest(any(), any());
  }

  @Test
  void consume_CompletedEvent_throws_StorageProviderException_when_minio_fails() {
    var completedMessage =
        createEventMessage(EventType.COMPLETED, "graph-completed.json", "document-id");

    RuntimeException s3Exception = new RuntimeException("S3 process failed");
    when(storageService.download(anyString(), anyString())).thenThrow(s3Exception);

    assertThatThrownBy(() -> consumer.consume(completedMessage, "topic", "0", 2L))
        .isSameAs(s3Exception);
  }

  @Test
  void consume_CompletedEvent_throws_UncheckedIOException_when_stream_disconnects_mid_read() {
    var completedMessage =
        createEventMessage(EventType.COMPLETED, "graph-completed.json", "document-id");

    InputStream stream = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
    when(storageService.download(anyString(), anyString())).thenReturn(stream);

    IOException ioException = new IOException("reset connection");
    given(objectMapper.readValue(stream, DocumentGraph.class))
        .willAnswer(
            (invocation) -> {
              throw ioException;
            });

    assertThatThrownBy(() -> consumer.consume(completedMessage, "topic", "0", 2L))
        .isInstanceOf(UncheckedIOException.class)
        .hasCause(ioException);
  }

  @Test
  void consume_DeletedEvent_throws_IllegalArgumentException_when_correlationId_is_null() {
    var deletedMessage = createEventMessage(EventType.DELETED, "graph-deleted.json", null);

    assertThatThrownBy(() -> consumer.consume(deletedMessage, "topic", "0", 2L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Received DELETED event with null or empty documentId");

    verify(ingestionService, never()).deleteDocumentGraph(anyString());
  }

  @Test
  void consume_ignores_unsupported_event_type() {
    var unknownMessage =
        createEventMessage(EventType.UNKNOWN, "graph-deleted.json", "existing-document-id");

    consumer.consume(unknownMessage, "topic", "0", 2L);

    verify(storageService, never()).download(any(), any());
    verify(ingestionService, never()).ingest(any(), any());
    verify(ingestionService, never()).deleteDocumentGraph(any());
  }

  private EventMessage createEventMessage(EventType eventType, String fileKey, String documentId) {
    EventPayload eventPayload = new EventPayload(fileKey, "bucket", "application/pdf");

    return new EventMessage(
        "message-id", "2026-10-16T16:51:23", documentId, "DOCUMENT", eventType, eventPayload);
  }
}
