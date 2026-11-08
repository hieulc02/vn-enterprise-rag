package com.hieulc.insightragingestion.service.graph;

import static com.hieulc.insightragingestion.factory.GraphTestDataFactory.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hieulc.insightragingestion.dto.graph.*;
import com.hieulc.insightragingestion.repository.graph.GraphCustomRepository;
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

  private static final String docId = "test-document-id";
  private static final String docKey = "test-document-key";
  private static final int BATCH_SIZE = 1000;

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(service, "batchSize", BATCH_SIZE);
  }

  @Test
  void ingest_partitions_list_and_run_in_strict_order() {

    int batchSize = 2500;

    List<DocumentChunk> chunks = generateListChunks(docKey, batchSize);
    List<Entity> entities = generateListEntities(batchSize);
    List<Relationship> relationships = generateListRelationships(batchSize);

    DocumentGraph graph = new DocumentGraph(docKey, chunks, entities, relationships);

    when(graphRepository.linkChunks(docId)).thenReturn(batchSize);

    service.ingest(docId, graph);

    verify(graphRepository, times(3)).batchInsertChunks(eq(docId), eq(docKey), anyList());
    verify(graphRepository, times(3)).batchInsertEntities(anyList());
    verify(graphRepository, times(3)).batchInsertRelationship(anyList());
    verify(graphRepository, times(1)).linkChunks(docId);

    InOrder inOrder = inOrder(graphRepository);
    inOrder.verify(graphRepository, times(3)).batchInsertChunks(eq(docId), eq(docKey), anyList());
    inOrder.verify(graphRepository, times(1)).linkChunks(docId);
    inOrder.verify(graphRepository, times(3)).batchInsertEntities(anyList());
    inOrder.verify(graphRepository, times(3)).batchInsertRelationship(anyList());
  }

  @Test
  void ingest_partition_list_with_appropriate_batches() {
    int batchSize = 2500;
    DocumentGraph graph =
        new DocumentGraph(
            docId,
            Collections.emptyList(),
            generateListEntities(batchSize),
            Collections.emptyList());

    service.ingest(docId, graph);

    verify(graphRepository, times(3)).batchInsertEntities(entityCaptor.capture());

    List<List<Entity>> capturedBatches = entityCaptor.getAllValues();
    assertThat(capturedBatches.get(0)).hasSize(1000);
    assertThat(capturedBatches.get(1)).hasSize(1000);
    assertThat(capturedBatches.get(2)).hasSize(500);
  }

  @Test
  void ingest_handle_graph_ingestion_with_null_value_safely() {
    DocumentGraph graph = new DocumentGraph(docKey, null, null, null);

    service.ingest(docId, graph);

    verify(graphRepository, never()).batchInsertChunks(eq(docId), eq(docKey), anyList());
    verify(graphRepository, never()).batchInsertEntities(anyList());
    verify(graphRepository, never()).batchInsertRelationship(anyList());
    verify(graphRepository, never()).linkChunks(docId);
  }

  @Test
  void ingest_handle_graph_ingestion_with_emptyList_safely() {
    DocumentGraph graph =
        new DocumentGraph(
            docKey, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

    service.ingest(docId, graph);

    verify(graphRepository, never()).batchInsertChunks(eq(docId), eq(docKey), anyList());
    verify(graphRepository, never()).batchInsertEntities(anyList());
    verify(graphRepository, never()).batchInsertRelationship(anyList());
    verify(graphRepository, never()).linkChunks(docId);
  }

  @Test
  void deleteDocumentGraph_delete_complete_document_graph() {
    when(graphRepository.deleteDocument(docId)).thenReturn(new GraphDeleteSummary(10, 20));

    service.deleteDocumentGraph(docId);
    verify(graphRepository, times(1)).deleteDocument(docId);
  }

  @Test
  void deleteDocumentGraph_delete_with_idempotent() {

    GraphDeleteSummary deleteSummary = new GraphDeleteSummary(0, 0);
    when(graphRepository.deleteDocument(docId)).thenReturn(deleteSummary);

    service.deleteDocumentGraph(docId);
    verify(graphRepository, times(1)).deleteDocument(docId);
    assertThat(deleteSummary.wasAlreadyDeleted()).isTrue();
  }
}
