package com.hieulc.insightragretrieval.repository;

import com.hieulc.insightragretrieval.config.properties.GraphSearchProperties;
import com.hieulc.insightragretrieval.dto.context.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Slf4j
@RequiredArgsConstructor
class GraphRepositoryImpl implements GraphCustomRepository {

  private static final List<String> TRAVERSAL_RELATIONSHIP_TYPES =
      List.of("COMPONENT_OF", "IS_CHILD_OF", "HAS_OBSERVATION", "REPORTED_BY");
  private static final List<String> SEMANTIC_RELATIONSHIP_TYPES =
      List.of("OWNS", "INVESTS_IN", "EXPLAINED_BY", "AFFECTED_BY", "HAS_VALUE", "RELATED_TO");

  private static final String TRAVERSAL_RELATIONSHIP_CYPHER =
      String.join("|", TRAVERSAL_RELATIONSHIP_TYPES);
  private static final String SEMANTIC_RELATIONSHIP_CYPHER =
      String.join("|", SEMANTIC_RELATIONSHIP_TYPES);

  static final String chunkHybridSearchCypher = buildChunkHybridSearchCypher();
  static final String nodeHybridSearchCypher = buildNodeHybridSearchCypher();
  static final String relationshipSemanticSearchCypher = buildRelationshipSemanticSearchCypher();

  private final Neo4jClient neo4jClient;
  private final GraphContextMapper rowMapper;
  private final GraphSearchProperties graphSearchProperties;

  @Override
  @Transactional(readOnly = true)
  public List<DocumentChunkContext> searchChunksHybrid(
      float[] queryVector, String keyword, int topK) {

    Map<String, Object> params =
        Map.of(
            "searchQuery",
            keyword,
            "queryVector",
            queryVector,
            "topK",
            topK,
            "rrfConstant",
            graphSearchProperties.rrfConstant(),
            "wFt",
            graphSearchProperties.weightVector(),
            "wVt",
            graphSearchProperties.weightFullText(),
            "minVectorScore",
            graphSearchProperties.minVectorScore());

    return new ArrayList<>(
        neo4jClient
            .query(chunkHybridSearchCypher)
            .bindAll(params)
            .fetchAs(DocumentChunkContext.class)
            .mappedBy(rowMapper::mapToChunkContext)
            .all());
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentNodeContext> searchNodeHybrid(float[] queryVector, String keyword, int topK) {

    Map<String, Object> params =
        Map.of(
            "searchQuery",
            keyword,
            "queryVector",
            queryVector,
            "topK",
            topK,
            "rrfConstant",
            graphSearchProperties.rrfConstant(),
            "wFt",
            graphSearchProperties.weightVector(),
            "wVt",
            graphSearchProperties.weightFullText(),
            "minVectorScore",
            graphSearchProperties.minVectorScore(),
            "maxEdges",
            5);

    return new ArrayList<>(
        neo4jClient
            .query(nodeHybridSearchCypher)
            .bindAll(params)
            .fetchAs(DocumentNodeContext.class)
            .mappedBy(rowMapper::mapToNodeContext)
            .all());
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentRelationshipContext> searchRelationshipSemantic(
      float[] queryVector, int topK) {
    return new ArrayList<>(
        neo4jClient
            .query(relationshipSemanticSearchCypher)
            .bindAll(
                Map.of(
                    "queryVector",
                    queryVector,
                    "topK",
                    topK,
                    "guardrail",
                    graphSearchProperties.edgeSemanticGuardrail()))
            .fetchAs(DocumentRelationshipContext.class)
            .mappedBy(rowMapper::mapToRelationshipContext)
            .all());
  }

  static String buildChunkHybridSearchCypher() {
    return """
        CYPHER 25
        WITH $queryVector AS queryVector,
             $searchQuery AS searchQuery,
             $topK AS topK,
             $rrfConstant AS rrfConstant,
             $wFt AS wFt,
             $wVt AS wVt,
             $minVectorScore AS minVectorScore
        CALL (queryVector, searchQuery, topK, rrfConstant, wFt, wVt, minVectorScore) {
          CALL
            db.index.fulltext.queryNodes(
              'chunk_fulltext',
              searchQuery,
              {limit: topK}
            )
            YIELD node, score
          WITH collect(node) AS nodes, rrfConstant, wFt
          UNWIND range(0, (size(nodes) - 1)) AS rank
          RETURN nodes[rank] AS chunk, (wFt / ((rrfConstant + rank) + 1.0)) AS score

        UNION ALL

          MATCH (c:DocumentChunk)
            SEARCH c IN (
              VECTOR INDEX chunk_embedding
              FOR queryVector
              LIMIT $topK
            ) SCORE as score
          WITH c, score
          WHERE score >= minVectorScore
          WITH collect(c) AS nodes, rrfConstant, wVt
          UNWIND range(0, (size(nodes) - 1)) AS rank
          RETURN nodes[rank] AS chunk, (wVt / ((rrfConstant + rank) + 1.0)) AS score
        }
        WITH chunk, sum(score) AS wrrf
        ORDER BY wrrf DESC, chunk.chunk_id ASC
        LIMIT $topK
        RETURN chunk.chunk_id AS chunk_id,
               chunk.text AS text,
               {document_id: chunk.document_id, page_number: chunk.page_number} AS metadata,
               wrrf
        ORDER BY wrrf DESC, chunk_id ASC
        """;
  }

  static String buildNodeHybridSearchCypher() {
    return """
        CYPHER 25
        WITH $queryVector AS queryVector,
             $searchQuery AS searchQuery,
             $topK AS topK,
             $rrfConstant AS rrfConstant,
             $wFt AS wFt,
             $wVt AS wVt,
             $minVectorScore AS minVectorScore
        CALL (queryVector, searchQuery, topK, rrfConstant, wFt, wVt, minVectorScore) {
          CALL
            db.index.fulltext.queryNodes(
              'node_fulltext',
              searchQuery,
              {limit: topK}
            )
            YIELD node, score
          WITH collect(node) AS nodes, rrfConstant, wFt
          UNWIND range(0, (size(nodes) - 1)) AS rank
          RETURN nodes[rank] AS node, (wFt / ((rrfConstant + rank) + 1.0)) AS score

        UNION ALL

          MATCH (node:Node)
            SEARCH node IN (
              VECTOR INDEX node_embedding
              FOR queryVector
              LIMIT $topK
            ) SCORE as score
          WITH node, score
          WHERE score >= minVectorScore
          WITH collect(node) AS nodes, rrfConstant, wVt
          UNWIND range(0, (size(nodes) - 1)) AS rank
          RETURN nodes[rank] AS node, (wVt / ((rrfConstant + rank) + 1.0)) AS score
        }
        WITH node AS anchor, sum(score) AS wrrf
        ORDER BY wrrf DESC, node.id ASC
        LIMIT $topK

        WITH anchor, wrrf,
             anchor {.*, embedding: null, title: null, id: null} AS anchor_props

        CALL (anchor) {
            MATCH (anchor)-[:EXTRACTED_FROM]->(c:DocumentChunk)
            WITH c
            LIMIT 3
            RETURN collect({
                chunk_id: c.chunk_id,
                metadata: {document_id: c.document_id, page_number: c.page_number}
            }) AS source_chunks
        }

        CALL (anchor) {
            MATCH (anchor)-[r:%s]-(target:Node)
            WITH r, target, anchor
            LIMIT $maxEdges

            RETURN collect({
               direction: CASE WHEN startNode(r) = anchor THEN 'OUTGOING' ELSE 'INCOMING' END,
               rel_type: type(r),
               rel_desc: coalesce(r.description, ""),
               target_id: coalesce(target.id, elementId(target)),
               target_title: target.title,
               target_labels: labels(target),
               target_props: target { .*, embedding: null, title: null, id: null }
            }) AS related_entities
        }

        RETURN coalesce(anchor.id, elementId(anchor)) as anchor_id,
               anchor.title as anchor_title,
               labels(anchor) as anchor_label,
               anchor_props,
               related_entities,
               source_chunks,
               wrrf
        ORDER BY wrrf DESC, anchor_id ASC
      """
        .formatted(TRAVERSAL_RELATIONSHIP_CYPHER);
  }

  static String buildRelationshipSemanticSearchCypher() {
    return """
        CYPHER 25
        MATCH ()-[r:%s]->()
        WHERE score >= $guardrail
          SEARCH r IN (
            VECTOR INDEX semantic_embedding
            FOR $queryVector
            LIMIT $topK
          ) SCORE AS score
        WITH r, score, startNode(r) AS src, endNode(r) AS tgt
        RETURN
          type(r) AS edge_type,
          r.description AS edge_desc,
          score AS semantic_score,
          {id: coalesce(src.id, elementId(src)), title: src.title, labels: labels(src)} AS source_node,
          {id: coalesce(tgt.id, elementId(tgt)), title: tgt.title, labels: labels(tgt)} AS target_node
        ORDER BY semantic_score DESC
    """
        .formatted(SEMANTIC_RELATIONSHIP_CYPHER);
  }
}
