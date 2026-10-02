package com.hieulc.insightragretrieval.service.context;

import static com.hieulc.insightragretrieval.service.context.GraphContextFormatter.*;
import static com.hieulc.insightragretrieval.util.LuceneQueryUtils.toOrQuery;

import com.hieulc.insightragretrieval.config.properties.ContextFusionProperties;
import com.hieulc.insightragretrieval.dto.context.*;
import com.hieulc.insightragretrieval.dto.query.QueryPreparation;
import com.hieulc.insightragretrieval.exception.appli.InsufficientContextException;
import com.hieulc.insightragretrieval.exception.infras.TokenExceededLimitException;
import com.hieulc.insightragretrieval.repository.GraphCustomRepository;
import com.hieulc.insightragretrieval.service.chat.cache.CachedQueryPreparationService;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContextFusionServiceImpl implements ContextFusionService {

  private final GraphCustomRepository graphRepository;
  private final TokenCountEstimator tokenCountEstimator;
  private final AsyncTaskExecutor virtualExecutor;
  private final CachedQueryPreparationService preparationService;

  private final ContextFusionProperties contextFusionProperties;

  @Override
  public FusionResult fuse(String query, int maxContextToken) {
    QueryPreparation preparedQuery = preparationService.prepare(query);
    String keywordQuery = toOrQuery(preparedQuery.analysis().extractKeyword());
    ContextFusionProperties.StrategyLimits limits = contextFusionProperties.getActiveLimits();
    return fuseContext(preparedQuery, keywordQuery, limits, maxContextToken);
  }

  private FusionResult fuseContext(
      QueryPreparation preparedQuery,
      String keyword,
      ContextFusionProperties.StrategyLimits limits,
      int maxContextToken) {

    CompletableFuture<List<DocumentChunkContext>> chunksFuture =
        fetchAsync(
            () ->
                graphRepository.searchChunksHybrid(
                    preparedQuery.vector(), keyword, limits.chunkLimit()),
            "Document chunks retrieval");

    CompletableFuture<List<DocumentNodeContext>> nodesFuture =
        fetchAsync(
            () ->
                graphRepository.searchNodeHybrid(
                    preparedQuery.vector(), keyword, limits.nodeLimit()),
            "Graph nodes retrieval");

    CompletableFuture<List<DocumentRelationshipContext>> edgesFuture =
        fetchAsync(
            () ->
                graphRepository.searchRelationshipSemantic(
                    preparedQuery.vector(), limits.relationshipLimit()),
            "Graph relationships retrieval");

    CompletableFuture.allOf(chunksFuture, nodesFuture, edgesFuture).join();

    List<DocumentChunkContext> chunks = chunksFuture.join();
    List<DocumentNodeContext> nodes = nodesFuture.join();
    List<DocumentRelationshipContext> edges = edgesFuture.join();

    if (chunks.isEmpty() && nodes.isEmpty() && edges.isEmpty()) {
      log.warn("All context retrieval failed or returned zero result for keyword: {}", keyword);
      throw new InsufficientContextException("No context could be retrieved to answer the query");
    }

    return aggregateAndDeduplicate(chunks, nodes, edges, maxContextToken);
  }

  private FusionResult aggregateAndDeduplicate(
      List<DocumentChunkContext> chunks,
      List<DocumentNodeContext> nodes,
      List<DocumentRelationshipContext> edges,
      int maxContextToken) {

    StringBuilder graphContext = new StringBuilder();
    int currentToken = 0;

    Set<String> seenContext = new HashSet<>();

    if (!chunks.isEmpty()) {
      StringBuilder chunkBlock = new StringBuilder();
      for (DocumentChunkContext chunk : chunks) {
        if (!seenContext.add("chunk:" + chunk.chunkId())) continue;

        String text = formatChunkContext(chunk);
        int tokens = countToken(text);
        if (currentToken + tokens > maxContextToken) {
          throw new TokenExceededLimitException("Document chunk exceed LLM token limit");
        }
        chunkBlock.append(text);
        currentToken += tokens;
      }
      if (!chunkBlock.isEmpty()) {
        graphContext.append(" <source_chunks>\n").append(chunkBlock).append(" </source_chunks>\n");
      }
    }

    if (!nodes.isEmpty()) {
      StringBuilder nodeBlock = new StringBuilder();
      for (DocumentNodeContext node : nodes) {
        if (!seenContext.add("node:" + node.documentNode().id())) continue;

        String text = formatNodeContext(node);
        int tokens = countToken(text);
        if (currentToken + tokens > maxContextToken) {
          return new FusionResult(graphContext.toString(), currentToken);
        }
        nodeBlock.append(text);
        currentToken += tokens;
      }
      if (!nodeBlock.isEmpty()) {
        graphContext.append(" <entities>\n").append(nodeBlock).append(" </entities>\n");
      }
    }

    if (!edges.isEmpty()) {
      StringBuilder edgeBlock = new StringBuilder();
      for (DocumentRelationshipContext edge : edges) {
        String edgeKey =
            "edge:"
                + edge.sourceNode().id()
                + "|"
                + edge.nodeEdge().edgeType()
                + "|"
                + edge.targetNode().id();
        if (!seenContext.add(edgeKey)) continue;
        String text = formatRelationshipContext(edge);

        int tokens = countToken(text);
        if (currentToken + tokens > maxContextToken) {
          return new FusionResult(graphContext.toString(), currentToken);
        }
        edgeBlock.append(text);
        currentToken += tokens;
      }

      if (!edgeBlock.isEmpty()) {
        graphContext
            .append(" <semantic_connections>\n")
            .append(edgeBlock)
            .append(" </semantic_connections>\n");
      }
    }

    return new FusionResult(graphContext.toString(), currentToken);
  }

  private <T> CompletableFuture<List<T>> fetchAsync(Supplier<List<T>> supplier, String taskName) {
    return CompletableFuture.supplyAsync(supplier, virtualExecutor)
        .orTimeout(contextFusionProperties.retrievalTimeoutSeconds(), TimeUnit.SECONDS)
        .exceptionally(
            e -> {
              Throwable cause = (e instanceof CompletionException) ? e.getCause() : e;
              log.error("{} failed", taskName, cause);
              return Collections.emptyList();
            });
  }

  private int countToken(String text) {
    return tokenCountEstimator.estimateTokenCountInText(text);
  }
}
