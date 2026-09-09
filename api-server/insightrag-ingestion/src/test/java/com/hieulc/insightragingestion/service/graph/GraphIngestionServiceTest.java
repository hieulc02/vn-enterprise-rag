package com.hieulc.insightragingestion.service.graph;

import static com.hieulc.insightragingestion.factory.GraphTestDataFactory.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hieulc.insightragingestion.dto.DocumentChunk;
import com.hieulc.insightragingestion.dto.DocumentGraph;
import com.hieulc.insightragingestion.dto.Entity;
import com.hieulc.insightragingestion.dto.Relationship;
import com.hieulc.insightragingestion.repository.GraphCustomRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GraphIngestionServiceTest {
  @Mock GraphCustomRepository graphRepository;

  @InjectMocks GraphIngestionService service;

  @Captor ArgumentCaptor<List<Entity>> entityCaptor;

  private static final String docId = "test-document";
  private static final int BATCH_SIZE = 1000;

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(service, "batchSize", BATCH_SIZE);
  }

  @Test
  void shouldPartitionDataAndRunInStrictOrder() {

    int batchSize = 2500;

    List<DocumentChunk> chunks = generateListChunks(docId, batchSize);
    List<Entity> entities = generateListEntities(batchSize);
    List<Relationship> relationships = generateListRelationships(batchSize);

    DocumentGraph graph = new DocumentGraph(docId, chunks, entities, relationships);

    when(graphRepository.linkChunks(docId)).thenReturn(batchSize);

    service.ingest(graph);

    verify(graphRepository, times(3)).batchInsertChunks(eq(docId), anyList());
    verify(graphRepository, times(3)).batchInsertEntities(anyList());
    verify(graphRepository, times(3)).batchInsertRelationship(anyList());
    verify(graphRepository, times(1)).linkChunks(docId);

    InOrder inOrder = inOrder(graphRepository);
    inOrder.verify(graphRepository, times(3)).batchInsertChunks(eq(docId), anyList());
    inOrder.verify(graphRepository, times(1)).linkChunks(docId);
    inOrder.verify(graphRepository, times(3)).batchInsertEntities(anyList());
    inOrder.verify(graphRepository, times(3)).batchInsertRelationship(anyList());
  }

  @Test
  void shouldPartitionDataWithAppropriateBatches() {
    int batchSize = 2500;
    DocumentGraph graph =
        new DocumentGraph(
            docId,
            Collections.emptyList(),
            generateListEntities(batchSize),
            Collections.emptyList());

    service.ingest(graph);

    verify(graphRepository, times(3)).batchInsertEntities(entityCaptor.capture());

    List<List<Entity>> capturedBatches = entityCaptor.getAllValues();
    assertThat(capturedBatches.get(0)).hasSize(1000);
    assertThat(capturedBatches.get(1)).hasSize(1000);
    assertThat(capturedBatches.get(2)).hasSize(500);
  }

  @Test
  void shouldHandleGraphWithNullListSafely() {
    DocumentGraph graph = new DocumentGraph(docId, null, null, null);

    service.ingest(graph);

    verify(graphRepository, never()).batchInsertChunks(eq(docId), anyList());
    verify(graphRepository, never()).batchInsertEntities(anyList());
    verify(graphRepository, never()).batchInsertRelationship(anyList());
    verify(graphRepository, never()).linkChunks(docId);
  }

  @Test
  void shouldHandleGraphWithEmptyListSafely() {
    DocumentGraph graph =
        new DocumentGraph(
            docId, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

    service.ingest(graph);

    verify(graphRepository, never()).batchInsertChunks(eq(docId), anyList());
    verify(graphRepository, never()).batchInsertEntities(anyList());
    verify(graphRepository, never()).batchInsertRelationship(anyList());
    verify(graphRepository, never()).linkChunks(docId);
  }
}
