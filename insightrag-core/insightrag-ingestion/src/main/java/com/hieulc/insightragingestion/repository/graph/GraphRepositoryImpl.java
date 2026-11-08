package com.hieulc.insightragingestion.repository.graph;

import static com.hieulc.insightragingestion.repository.graph.GraphCypherProvider.*;

import com.hieulc.insightragingestion.dto.graph.DocumentChunk;
import com.hieulc.insightragingestion.dto.graph.Entity;
import com.hieulc.insightragingestion.dto.graph.GraphDeleteSummary;
import com.hieulc.insightragingestion.dto.graph.Relationship;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class GraphRepositoryImpl implements GraphCustomRepository {

  private final Neo4jClient neo4jClient;

  private final String mergeChunksNodeAndEdgeCypher = buildMergeChunksNodeAndEdgeCypher();
  private final String mergeEntityCypher = buildMergeEntityCypher();
  private final String mergeEntityEdgeCypher = buildMergeEntityEdgeCypher();
  private final String linkChunksCypher = buildLinkChunksCypher();
  private final String deleteDocumentCypher = buildDeleteDocumentCypher();

  private final Map<String, String> relationshipCypherCache = new ConcurrentHashMap<>();

  public GraphRepositoryImpl(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
  }

  @Override
  @Transactional
  public void batchInsertChunks(String documentId, String documentKey, List<DocumentChunk> chunks) {
    List<Map<String, Object>> batch = chunks.stream().map(GraphMapper::toChunkMap).toList();

    neo4jClient
        .query(mergeChunksNodeAndEdgeCypher)
        .bind(batch)
        .to("batch")
        .bind(documentId)
        .to("docId")
        .bind(documentKey)
        .to("docKey")
        .run();
  }

  @Override
  @Transactional
  public void batchInsertEntities(List<Entity> entities) {
    List<Map<String, Object>> nodeBatch =
        entities.stream().map(GraphMapper::toEntityNodeMap).toList();

    neo4jClient.query(mergeEntityCypher).bind(nodeBatch).to("batch").run();

    List<Map<String, Object>> edgeBatch =
        entities.stream().filter(Entity::hasSourceChunk).map(GraphMapper::toEntityEdgeMap).toList();

    if (!edgeBatch.isEmpty()) {
      neo4jClient.query(mergeEntityEdgeCypher).bind(edgeBatch).to("batch").run();
    }
  }

  @Override
  @Transactional
  public void batchInsertRelationship(List<Relationship> relationships) {
    Map<String, List<Map<String, Object>>> groupedRelationship = new HashMap<>();

    relationships.forEach(
        rel -> {
          Map<String, Object> batchMap = GraphMapper.toRelationshipMap(rel);
          groupedRelationship.computeIfAbsent(rel.type(), k -> new ArrayList<>()).add(batchMap);
        });

    for (Map.Entry<String, List<Map<String, Object>>> entry : groupedRelationship.entrySet()) {
      String type = entry.getKey();
      List<Map<String, Object>> batch = entry.getValue();

      String relationshipCypher =
          relationshipCypherCache.computeIfAbsent(
              type, GraphCypherProvider::buildMergeRelationshipCypher);

      neo4jClient.query(relationshipCypher).bind(batch).to("batch").run();
    }
  }

  @Override
  @Transactional
  public int linkChunks(String documentId) {
    return neo4jClient
        .query(linkChunksCypher)
        .bind(documentId)
        .to("docId")
        .fetchAs(Integer.class)
        .all()
        .stream()
        .findFirst()
        .orElse(0);
  }

  @Override
  @Transactional
  public GraphDeleteSummary deleteDocument(String documentId) {
    var summary = neo4jClient.query(deleteDocumentCypher).bind(documentId).to("docId").run();
    var counters = summary.counters();
    return new GraphDeleteSummary(counters.nodesDeleted(), counters.relationshipsDeleted());
  }
}
