package com.hieulc.insightragretrieval.service.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.hieulc.insightragretrieval.dto.QueryAnalysis;
import com.hieulc.insightragretrieval.dto.context.*;
import com.hieulc.insightragretrieval.exception.appli.InsufficientContextException;
import com.hieulc.insightragretrieval.repository.GraphCustomRepository;
import com.hieulc.insightragretrieval.service.chat.extractor.QueryAnalyzer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

@ExtendWith(MockitoExtension.class)
class ContextFusionServiceImplTest {

  @Mock private GraphCustomRepository graphCustomRepository;
  @Mock private TokenCountEstimator tokenCountEstimator;
  @Mock private AsyncTaskExecutor virtualExecutor;
  @Mock private QueryAnalyzer queryAnalyzer;
  @Mock private EmbeddingModel embeddingModel;

  private ContextFusionServiceImpl contextFusionService;
  private final float[] DUMMY_VECTOR = new float[] {0.1f};
  private final String keyword = "Chunk";
  private String documentId = "test-document";
  private final int MAX_TOKENS = 100;
  private final String query = "Entity is a Subject";

  @BeforeEach
  void setUp() {

    doAnswer(
            invocation -> {
              Runnable task = invocation.getArgument(0);
              task.run();
              return null;
            })
        .when(virtualExecutor)
        .execute(any(Runnable.class));

    contextFusionService =
        new ContextFusionServiceImpl(
            graphCustomRepository,
            tokenCountEstimator,
            virtualExecutor,
            queryAnalyzer,
            embeddingModel);
  }

  @Test
  void fuse_returns_fusion_context_success_within_token_limit() {
    QueryAnalysis analysis = createAnalysis();
    when(queryAnalyzer.analyze(anyString())).thenReturn(analysis);
    when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(DUMMY_VECTOR)));

    DocumentNodeContext nodeContext =
        createNodeContext(
            "entity-1",
            "Entity",
            Map.of(),
            List.of(createNode("subject-1", "Subject", Map.of())),
            List.of(createEdge("COMPONENT_OF", null)));

    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of(createChunkContext("chunk-1", "test-chunk", 1)));
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of(nodeContext));
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt()))
        .thenReturn(List.of(createRelationshipContext()));

    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(20);

    FusionResult result = contextFusionService.fuse(query, MAX_TOKENS);

    assertThat(result).isNotNull();
    assertThat(result.totalTokens()).isEqualTo(100);
    assertThat(result.context())
        .contains(
            "test-chunk",
            "1",
            documentId,
            "Entity",
            "COMPONENT_OF",
            "Subject",
            "RELATED_TO",
            "default-desc",
            "Source-obs",
            "Target-obs");
  }

  @Test
  void fuse_stops_aggregating_when_token_limit_reached() {
    QueryAnalysis analysis = createAnalysis();
    when(queryAnalyzer.analyze(anyString())).thenReturn(analysis);
    when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(DUMMY_VECTOR)));

    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenReturn(
            List.of(
                createChunkContext("chunk-1", "test-chunk-1", 1),
                createChunkContext("chunk-2", "test-chunk-2", 2)));
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt())).thenReturn(List.of());

    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(60);

    FusionResult result = contextFusionService.fuse(query, MAX_TOKENS);

    assertThat(result).isNotNull();
    assertThat(result.totalTokens()).isEqualTo(60);
    assertThat(result.context()).contains("test-chunk-1", "1");
    assertThat(result.context()).doesNotContain("test-chunk-2", "2");
  }

  @Test
  void fuse_handles_repository_exception_gracefully() {
    QueryAnalysis analysis = createAnalysis();
    when(queryAnalyzer.analyze(anyString())).thenReturn(analysis);
    when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(DUMMY_VECTOR)));

    DocumentNodeContext nodeContext =
        createNodeContext(
            "entity-2",
            "Entity",
            Map.of(),
            List.of(createNode("subject-2", "Subject", Map.of())),
            List.of(createEdge("AFFECTED_BY", null)));

    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenThrow(new RuntimeException("DB connection timeout"));
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of(nodeContext));
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt()))
        .thenReturn(Collections.emptyList());

    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(20);

    FusionResult result = contextFusionService.fuse(query, MAX_TOKENS);

    assertThat(result.totalTokens()).isGreaterThan(0);
    assertThat(result.context()).contains("Entity", "AFFECTED_BY", "Subject");
  }

  @Test
  void fuse_fails_fast_when_all_retrieval_return_empty() {
    QueryAnalysis analysis = createAnalysis();
    when(queryAnalyzer.analyze(anyString())).thenReturn(analysis);
    when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(DUMMY_VECTOR)));

    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt())).thenReturn(List.of());

    assertThatThrownBy(() -> contextFusionService.fuse(query, MAX_TOKENS))
        .isInstanceOf(InsufficientContextException.class)
        .hasMessage("No context could be retrieved to answer the query");
  }

  @Test
  void fuse_filters_out_node_properties_when_building_node_props() {
    QueryAnalysis analysis = createAnalysis();
    when(queryAnalyzer.analyze(anyString())).thenReturn(analysis);
    when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(DUMMY_VECTOR)));

    DocumentNodeContext nodeContext =
        createNodeContext(
            "entity-4",
            "Subject",
            Map.of("name", "Object", "embedding", List.of(0.1, 0.2)),
            List.of(),
            List.of());
    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of(nodeContext));
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt())).thenReturn(List.of());

    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(20);

    FusionResult result = contextFusionService.fuse(query, MAX_TOKENS);

    assertThat(result.context()).contains("Subject", "name", "Object");
    assertThat(result.context()).doesNotContain("embedding");
    assertThat(result.context()).doesNotContain("0.1", "0.2");
  }

  @Test
  void fuse_filters_out_node_properties_when_building_edge_props() {
    QueryAnalysis analysis = createAnalysis();
    when(queryAnalyzer.analyze(anyString())).thenReturn(analysis);
    when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(DUMMY_VECTOR)));

    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt()))
        .thenReturn(List.of(createRelationshipContext()));

    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(20);

    FusionResult result = contextFusionService.fuse(query, MAX_TOKENS);

    assertThat(result.context())
        .contains("Source-obs", "RELATED_TO", "default-desc", "Target-obs", "value", "123");
    assertThat(result.context()).doesNotContain("embedding");
    assertThat(result.context()).doesNotContain("0.3", "0.4");
  }

  private QueryAnalysis createAnalysis() {
    return new QueryAnalysis("optimized", List.of("Entity", "Subject"), null);
  }

  private DocumentChunkContext createChunkContext(String chunkId, String text, int pageNumber) {
    return new DocumentChunkContext(
        chunkId,
        text,
        new DocumentChunkContext.ChunkMetadata(documentId, pageNumber),
        List.of(),
        1.0);
  }

  private DocumentRelationshipContext createRelationshipContext() {
    return new DocumentRelationshipContext(
        new NodeEdge("RELATED_TO", "default-desc"),
        1.0,
        new DocumentRelationshipContext.DocumentNodeContext(
            createNode("source-id", "Source-obs", Map.of("embedding", List.of(0.3, 0.4))),
            List.of(Map.of())),
        new DocumentRelationshipContext.DocumentNodeContext(
            createNode(
                "target-id",
                "Target-obs",
                Map.of("value", 123, "embedding", Collections.emptyList())),
            List.of(Map.of())));
  }

  private DocumentNodeContext createNodeContext(
      String id,
      String title,
      Map<String, Object> properties,
      List<DocumentNode> nodes,
      List<NodeEdge> edges) {
    return new DocumentNodeContext(
        createNode(id, title, properties),
        1.0,
        List.of(new DocumentNodeContext.StructuralPath(nodes, edges)));
  }

  private DocumentNode createNode(String id, String title, Map<String, Object> properties) {
    return new DocumentNode(id, title, List.of(), properties);
  }

  private NodeEdge createEdge(String edgeType, String edgeDesc) {
    return new NodeEdge(edgeType, edgeDesc);
  }
}
