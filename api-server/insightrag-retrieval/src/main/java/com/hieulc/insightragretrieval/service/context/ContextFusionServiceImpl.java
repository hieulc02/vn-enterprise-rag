package com.hieulc.insightragretrieval.service.context;

import static com.hieulc.insightragretrieval.util.LuceneQueryUtils.toOrQuery;

import com.hieulc.insightragretrieval.dto.QueryAnalysis;
import com.hieulc.insightragretrieval.dto.context.*;
import com.hieulc.insightragretrieval.exception.appli.InsufficientContextException;
import com.hieulc.insightragretrieval.repository.GraphCustomRepository;
import com.hieulc.insightragretrieval.service.chat.extractor.QueryAnalyzer;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
  private final QueryAnalyzer queryAnalyzer;
  private final EmbeddingModel embeddingModel;

  @Override
  public FusionResult fuse(String query, int maxContextToken) {
    QueryAnalysis analysis = queryAnalyzer.analyze(query);
    float[] vector = embeddingModel.embed(analysis.optimizedVectorQuery()).content().vector();
    return fuseContext(vector, toOrQuery(analysis.extractKeyword()), maxContextToken);
  }

  private FusionResult fuseContext(float[] vector, String keyword, int maxContextToken) {
    CompletableFuture<List<DocumentChunkContext>> chunksFuture =
        CompletableFuture.supplyAsync(
                () -> graphRepository.searchChunksHybrid(vector, keyword, 3), virtualExecutor)
            .orTimeout(10, TimeUnit.SECONDS)
            .exceptionally(
                e -> {
                  log.error("Chunk query failed: {}", e.getMessage());
                  return List.of();
                });

    CompletableFuture<List<DocumentNodeContext>> nodesFuture =
        CompletableFuture.supplyAsync(
                () -> graphRepository.searchNodeHybrid(vector, keyword, 5), virtualExecutor)
            .orTimeout(10, TimeUnit.SECONDS)
            .exceptionally(
                e -> {
                  log.error("Node query failed: {}", e.getMessage());
                  return List.of();
                });

    CompletableFuture<List<DocumentRelationshipContext>> edgesFuture =
        CompletableFuture.supplyAsync(
                () -> graphRepository.searchRelationshipSemantic(vector, 5), virtualExecutor)
            .orTimeout(10, TimeUnit.SECONDS)
            .exceptionally(
                e -> {
                  log.error("Relationship query failed: {}", e.getMessage());
                  return List.of();
                });

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

    StringBuilder context = new StringBuilder();
    int currentToken = 0;

    for (DocumentChunkContext chunk : chunks) {
      String chunkContext = buildChunkContext(chunk);
      int tokens = countToken(chunkContext);
      if (currentToken + tokens > maxContextToken) {
        return new FusionResult(context.toString(), currentToken);
      }
      context.append(chunkContext);
      currentToken += tokens;
    }

    for (DocumentRelationshipContext edge : edges) {
      String edgeContext = buildEdgeContext(edge);
      int tokens = countToken(edgeContext);
      if (currentToken + tokens > maxContextToken) {
        return new FusionResult(context.toString(), currentToken);
      }
      context.append(edgeContext);
      currentToken += tokens;
    }

    Map<String, String> uniqueNodes = new LinkedHashMap<>();

    for (DocumentNodeContext node : nodes) {
      if (node.structuralPaths().isEmpty()) {
        uniqueNodes.putIfAbsent(
            node.documentNode().id(), buildNodeContext(node.documentNode(), List.of(), List.of()));
        continue;
      }
      for (DocumentNodeContext.StructuralPath path : node.structuralPaths()) {
        String mapId = buildMapId(node.documentNode(), path.edges());
        uniqueNodes.putIfAbsent(
            mapId, buildNodeContext(node.documentNode(), path.nodes(), path.edges()));
      }
    }

    for (DocumentRelationshipContext edge : edges) {
      List<NodeEdge> obsEdge = List.of(new NodeEdge("HAS_OBSERVATION", null));
      String sourceMapId = buildMapId(edge.sourceNode().nodeMetadata(), obsEdge);
      uniqueNodes.putIfAbsent(
          sourceMapId,
          buildNodeContext(
              edge.sourceNode().nodeMetadata(),
              List.of(edge.sourceNode().nodeMetadata()),
              obsEdge));

      String targetMapId = buildMapId(edge.targetNode().nodeMetadata(), obsEdge);
      uniqueNodes.putIfAbsent(
          targetMapId,
          buildNodeContext(
              edge.targetNode().nodeMetadata(),
              List.of(edge.targetNode().nodeMetadata()),
              obsEdge));
    }

    for (String nodeContext : uniqueNodes.values()) {
      int tokens = countToken(nodeContext);
      if (currentToken + tokens > maxContextToken) {
        return new FusionResult(context.toString(), currentToken);
      }
      context.append(nodeContext);
      currentToken += tokens;
    }

    return new FusionResult(context.toString(), currentToken);
  }

  private String buildChunkContext(DocumentChunkContext chunk) {
    return "<chunks>\n"
        + chunk.text()
        + "\n"
        + "Source: "
        + chunk.metadata().documentId()
        + " Pages: "
        + chunk.metadata().pageNumber()
        + "</chunks>\n";
  }

  private String buildEdgeContext(DocumentRelationshipContext edge) {
    return "<graph_relationships>\n"
        + edge.sourceNode().nodeMetadata().title()
        + buildNodeProps(edge.sourceNode().nodeMetadata().properties())
        + " -["
        + edge.nodeEdge().edgeType()
        + ":"
        + edge.nodeEdge().edgeDesc()
        + "]"
        + "-> "
        + edge.targetNode().nodeMetadata().title()
        + buildNodeProps(edge.targetNode().nodeMetadata().properties())
        + "</graph_relationships>\n";
  }

  private String buildMapId(DocumentNode node, List<NodeEdge> edges) {
    StringBuilder idSb = new StringBuilder();
    idSb.append(node.id());
    edges.stream()
        .filter(Objects::nonNull)
        .forEach(edge -> idSb.append(":").append(edge.edgeType()));
    return idSb.toString();
  }

  private String buildNodeContext(
      DocumentNode sourceNode, List<DocumentNode> targetNodes, List<NodeEdge> edges) {
    StringBuilder context = new StringBuilder();
    context
        .append("<graph_entities>\n")
        .append(sourceNode.title())
        .append(buildNodeProps(sourceNode.properties()))
        .append("\n");

    if (targetNodes == null || edges.isEmpty()) {
      context.append("</graph_entities>\n");
      return context.toString();
    }

    context.append("Path: ");
    context
        .append(targetNodes.getFirst().title())
        .append(" -[")
        .append(edges.getFirst().edgeType())
        .append("]");
    for (int i = 1; i < targetNodes.size(); i++) {
      context
          .append("-> ")
          .append(targetNodes.get(i).title())
          .append(buildNodeProps(targetNodes.get(i).properties()));

      if (i < edges.size() && edges.get(i) != null) {
        context.append(" -[").append(edges.get(i).edgeType()).append("]");
      }
    }
    context.append("</graph_entities>\n");
    return context.toString();
  }

  private String buildNodeProps(Map<String, Object> props) {
    if (props == null || props.isEmpty()) {
      return "{}";
    }

    String stringProps =
        props.entrySet().stream()
            .filter(entry -> !"embedding".equals(entry.getKey()))
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining(", "));

    return "{" + stringProps + "}";
  }

  private int countToken(String text) {
    return tokenCountEstimator.estimateTokenCountInText(text);
  }
}
