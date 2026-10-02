package com.hieulc.insightragretrieval.service.context;

import static com.hieulc.insightragretrieval.factory.ContextDataTestFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.hieulc.insightragretrieval.config.properties.ContextFusionProperties;
import com.hieulc.insightragretrieval.dto.context.*;
import com.hieulc.insightragretrieval.dto.query.QueryAnalysis;
import com.hieulc.insightragretrieval.dto.query.QueryPreparation;
import com.hieulc.insightragretrieval.exception.appli.InsufficientContextException;
import com.hieulc.insightragretrieval.repository.GraphCustomRepository;
import com.hieulc.insightragretrieval.service.chat.cache.CachedQueryPreparationService;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

@ExtendWith(MockitoExtension.class)
class ContextFusionServiceImplTest {

  @Mock private GraphCustomRepository graphCustomRepository;
  @Mock private TokenCountEstimator tokenCountEstimator;
  @Mock private AsyncTaskExecutor virtualExecutor;
  @Mock private CachedQueryPreparationService preparationService;

  private ContextFusionProperties contextFusionProperties;

  @InjectMocks private ContextFusionServiceImpl contextFusionService;
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

    contextFusionProperties =
        new ContextFusionProperties(
            5, "HYBRID", Map.of("HYBRID", new ContextFusionProperties.StrategyLimits(3, 3, 3)));

    contextFusionService =
        new ContextFusionServiceImpl(
            graphCustomRepository,
            tokenCountEstimator,
            virtualExecutor,
            preparationService,
            contextFusionProperties);
  }

  @Test
  void fuse_aggregates_graph_context_within_token_limit() {
    when(preparationService.prepare(anyString())).thenReturn(cacheQueryPreparation());

    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of(createDocumentChunkContext("chunk-1")));
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of(createDocumentNodeContext()));
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt()))
        .thenReturn(List.of(createRelationshipContext("source", "target")));

    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(20);

    FusionResult result = contextFusionService.fuse(query, MAX_TOKENS);

    assertThat(result).isNotNull();
    assertThat(result.totalTokens()).isEqualTo(60);
    assertThat(result.context())
        .contains(
            "chunk-1",
            "doc-test",
            "anchor",
            "value",
            "props-anchor",
            "COMPONENT_OF",
            "source",
            "target",
            "RELATED_TO",
            "123.0");
  }

  @Test
  void fuse_deduplicates_graph_context_within_token_limit() {
    when(preparationService.prepare(anyString())).thenReturn(cacheQueryPreparation());

    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenReturn(
            List.of(createDocumentChunkContext("chunk-1"), createDocumentChunkContext("chunk-1")));
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of(createDocumentNodeContext(), createDocumentNodeContext()));
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt()))
        .thenReturn(
            List.of(
                createRelationshipContext("source", "target"),
                createRelationshipContext("source", "target")));

    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(20);

    FusionResult result = contextFusionService.fuse(query, MAX_TOKENS);

    assertThat(result).isNotNull();
    assertThat(result.totalTokens()).isEqualTo(60);
    assertThat(result.context())
        .containsOnlyOnce("chunk-1")
        .containsOnlyOnce("COMPONENT_OF")
        .containsOnlyOnce("RELATED_TO");
  }

  @Test
  void fuse_stops_fusing_graph_context_when_token_limit_exceeded() {
    when(preparationService.prepare(anyString())).thenReturn(cacheQueryPreparation());
    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenReturn(
            List.of(createDocumentChunkContext("chunk-1"), createDocumentChunkContext("chunk-2")));
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt())).thenReturn(List.of());

    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(60);

    FusionResult result = contextFusionService.fuse(query, MAX_TOKENS);

    assertThat(result).isNotNull();
    assertThat(result.totalTokens()).isEqualTo(60);
    assertThat(result.context()).contains("chunk-1");
    assertThat(result.context()).doesNotContain("chunk-2");
  }

  @Test
  void fuse_handles_repository_exception_gracefully() {
    when(preparationService.prepare(anyString())).thenReturn(cacheQueryPreparation());

    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenThrow(new RuntimeException("DB connection timeout"));
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of(createDocumentNodeContext()));
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt()))
        .thenReturn(Collections.emptyList());

    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(20);

    FusionResult result = contextFusionService.fuse(query, MAX_TOKENS);

    assertThat(result.totalTokens()).isGreaterThan(0);
    assertThat(result.context()).contains("anchor", "value", "props-anchor");
  }

  @Test
  void fuse_fails_fast_when_all_retrieval_return_empty() {
    when(preparationService.prepare(anyString())).thenReturn(cacheQueryPreparation());
    when(graphCustomRepository.searchChunksHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(graphCustomRepository.searchNodeHybrid(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(graphCustomRepository.searchRelationshipSemantic(any(), anyInt())).thenReturn(List.of());

    assertThatThrownBy(() -> contextFusionService.fuse(query, MAX_TOKENS))
        .isInstanceOf(InsufficientContextException.class)
        .hasMessage("No context could be retrieved to answer the query");
  }

  private QueryPreparation cacheQueryPreparation() {
    return new QueryPreparation(
        new QueryAnalysis("optimized", List.of("Entity", "Subject")), new float[] {0.1f});
  }
}
