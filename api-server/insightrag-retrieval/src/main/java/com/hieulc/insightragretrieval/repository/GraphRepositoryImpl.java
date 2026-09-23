package com.hieulc.insightragretrieval.repository;

import com.google.common.annotations.VisibleForTesting;
import com.hieulc.insightragretrieval.dto.context.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class GraphRepositoryImpl implements GraphCustomRepository {

  private static final List<String> TRAVERSAL_REL_TYPES =
      List.of(
          "OWNS",
          "INVESTS_IN",
          "COMPONENT_OF",
          "AFFECTED_BY",
          "REPORTED_BY",
          "IS_CHILD_OF",
          "HAS_OBSERVATION",
          "HAS_VALUE");

  private static final String TRAVERSAL_REL_CYPHER = String.join("|", TRAVERSAL_REL_TYPES);

  private final Neo4jClient neo4jClient;
  private final Renderer cypherRenderer;
  private final String chunkHybridSearchCypher;
  private final String nodeHybridSearchCypher;
  private final String relationshipSemanticSearchCypher;

  public GraphRepositoryImpl(Neo4jClient neo4jClient, Renderer cypherRenderer) {
    this.neo4jClient = neo4jClient;
    this.cypherRenderer = cypherRenderer;
    this.chunkHybridSearchCypher = buildChunkHybridSearchCypher();
    this.nodeHybridSearchCypher = buildNodeHybridSearchCypher();
    this.relationshipSemanticSearchCypher = buildRelationshipSemanticSearchCypher();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentChunkContext> searchChunksHybrid(
      float[] queryVector, String keyword, int topK) {

    return new ArrayList<>(
        neo4jClient
            .query(chunkHybridSearchCypher)
            .bindAll(
                Map.of(
                    "queryVector",
                    queryVector,
                    "searchQuery",
                    keyword,
                    "topK",
                    topK,
                    "rrfConstant",
                    60,
                    "weightFulltext",
                    1.0,
                    "weightVector",
                    1.0))
            .fetchAs(DocumentChunkContext.class)
            .mappedBy(
                (typeSystem, record) -> {
                  Value metadata = record.get("metadata");
                  DocumentChunkContext.ChunkMetadata chunkMetadata =
                      new DocumentChunkContext.ChunkMetadata(
                          metadata.get("document_id").asString(),
                          metadata.get("page_number").asInt());

                  return new DocumentChunkContext(
                      record.get("chunk_id").asString(),
                      record.get("text").asString(),
                      chunkMetadata,
                      record.get("linked_entities").asList(Value::asString),
                      record.get("wrrf").asDouble());
                })
            .all());
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentNodeContext> searchNodeHybrid(float[] queryVector, String keyword, int topK) {
    return new ArrayList<>(
        neo4jClient
            .query(nodeHybridSearchCypher)
            .bindAll(
                Map.of(
                    "queryVector",
                    queryVector,
                    "searchQuery",
                    keyword,
                    "topK",
                    topK,
                    "topTraversal",
                    30,
                    "rrfConstant",
                    60,
                    "weightFulltext",
                    1.0,
                    "weightVector",
                    1.0))
            .fetchAs(DocumentNodeContext.class)
            .mappedBy(
                (typeSystem, record) -> {
                  List<DocumentNodeContext.StructuralPath> paths =
                      record
                          .get("structural_paths")
                          .asList(
                              pathValue -> {
                                List<DocumentNode> nodes =
                                    pathValue
                                        .get("nodes")
                                        .asList(
                                            value ->
                                                new DocumentNode(
                                                    value.get("id").asString(),
                                                    value.get("title").asString(),
                                                    value.get("labels").asList(Value::asString),
                                                    value.get("props").asMap()));

                                List<NodeEdge> edges =
                                    pathValue
                                        .get("edges")
                                        .asList(
                                            value ->
                                                new NodeEdge(
                                                    value.get("type").asString(),
                                                    value.get("desc").asString()));

                                return new DocumentNodeContext.StructuralPath(nodes, edges);
                              });

                  DocumentNode node =
                      new DocumentNode(
                          record.get("anchor_id").asString(),
                          record.get("anchor_title").asString(),
                          record.get("anchor_labels").asList(Value::asString),
                          record.get("anchor_props").asMap());

                  return new DocumentNodeContext(node, record.get("wrrf").asDouble(), paths);
                })
            .all());
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentRelationshipContext> searchRelationshipSemantic(
      float[] queryVector, int topK) {
    return new ArrayList<>(
        neo4jClient
            .query(relationshipSemanticSearchCypher)
            .bindAll(Map.of("queryVector", queryVector, "topK", topK, "guardrail", 0.7))
            .fetchAs(DocumentRelationshipContext.class)
            .mappedBy(
                ((typeSystem, record) -> {
                  Value sourceMap = record.get("source_node");
                  DocumentRelationshipContext.DocumentNodeContext sourceContext =
                      new DocumentRelationshipContext.DocumentNodeContext(
                          new DocumentNode(
                              sourceMap.get("id").asString(),
                              sourceMap.get("title").asString(),
                              sourceMap.get("labels").asList(Value::asString),
                              sourceMap.get("props").asMap()),
                          sourceMap.get("observations").asList(Value::asMap));

                  Value targetMap = record.get("target_node");
                  DocumentRelationshipContext.DocumentNodeContext targetContext =
                      new DocumentRelationshipContext.DocumentNodeContext(
                          new DocumentNode(
                              targetMap.get("id").asString(),
                              targetMap.get("title").asString(),
                              targetMap.get("labels").asList(Value::asString),
                              targetMap.get("props").asMap()),
                          targetMap.get("observations").asList(Value::asMap));

                  return new DocumentRelationshipContext(
                      new NodeEdge(
                          record.get("edge_type").asString(), record.get("edge_desc").asString()),
                      record.get("semantic_score").asDouble(),
                      sourceContext,
                      targetContext);
                }))
            .all());
  }

  @VisibleForTesting
  String buildChunkHybridSearchCypher() {
    var queryVector = Cypher.parameter("queryVector");
    var searchQuery = Cypher.parameter("searchQuery");
    var rrfConst = Cypher.parameter("rrfConstant");
    var topK = Cypher.parameter("topK");
    var weightFulltext = Cypher.parameter("weightFulltext");
    var weightVector = Cypher.parameter("weightVector");

    var limitK = Cypher.name("limitK");
    var kq = Cypher.name("kq");
    var qv = Cypher.name("qv");
    var rrfConstant = Cypher.name("rrfConstant");
    var wFt = Cypher.name("wFt");
    var wVt = Cypher.name("wVt");

    var rank = Cypher.name("rank");
    var node = Cypher.name("node");
    var nodes = Cypher.name("nodes");
    var score = Cypher.name("score");

    var wrrf = Cypher.name("wrrf");

    Statement ftStatement =
        Cypher.with(kq, limitK, rrfConstant, wFt)
            .call("db.index.fulltext.queryNodes")
            .withArgs(Cypher.literalOf("chunk_fulltext"), kq, Cypher.mapOf("limit", limitK))
            .yield(node, score)
            .with(node, score, rrfConstant, wFt)
            .orderBy(score.descending(), node.property("chunk_id").ascending())
            .with(Cypher.collect(node).as(nodes), rrfConstant, wFt)
            .unwind(
                Cypher.range(Cypher.literalOf(0), Cypher.size(nodes).subtract(Cypher.literalOf(1))))
            .as(rank)
            .returning(
                Cypher.valueAt(nodes, rank).as(node),
                wFt.divide(rrfConstant.add(rank).add(Cypher.literalOf(1.0))).as(score))
            .build();

    Statement vectorStatement =
        Cypher.with(qv, limitK, rrfConstant, wVt)
            .call("db.index.vector.queryNodes")
            .withArgs(Cypher.literalOf("chunk_embedding"), limitK, qv)
            .yield(node, score)
            .with(node, score, rrfConstant, wVt)
            .orderBy(score.descending(), node.property("chunk_id").ascending())
            .with(Cypher.collect(node).as(nodes), rrfConstant, wVt)
            .unwind(
                Cypher.range(Cypher.literalOf(0), Cypher.size(nodes).subtract(Cypher.literalOf(1))))
            .as(rank)
            .returning(
                Cypher.valueAt(nodes, rank).as(node),
                wVt.divide(rrfConstant.add(rank).add(Cypher.literalOf(1.0))).as(score))
            .build();

    Node src = Cypher.node("Node").named("src");
    Node cNode = Cypher.anyNode("node");

    var linkedEntities = Cypher.name("linked_entities");

    var matchExtractedFrom =
        Cypher.optionalMatch(src.relationshipTo(cNode, "EXTRACTED_FROM"))
            .returning(Cypher.collectDistinct(src.property("id")).as(linkedEntities))
            .build();

    Statement chunkStatement =
        Cypher.with(
                queryVector.as(qv),
                searchQuery.as(kq),
                topK.as(limitK),
                rrfConst.as(rrfConstant),
                Cypher.toFloat(weightFulltext).as(wFt),
                Cypher.toFloat(weightVector).as(wVt))
            .call(Cypher.unionAll(ftStatement, vectorStatement))
            .with(node, Cypher.sum(score).as(wrrf), limitK)
            .orderBy(wrrf.descending(), node.property("chunk_id").ascending())
            .limit(topK)
            .call(matchExtractedFrom, node)
            //            .optionalMatch(src.relationshipTo(cNode, "EXTRACTED_FROM"))
            .returning(
                node.property("chunk_id").as("chunk_id"),
                node.property("text").as("text"),
                Cypher.mapOf(
                        "document_id", node.property("document_id"),
                        "page_number", node.property("page_number"))
                    .as("metadata"),
                linkedEntities,
                // Cypher.collectDistinct(src.property("title")).as("linked_entities"),
                wrrf)
            .orderBy(wrrf.descending(), Cypher.name("chunk_id").ascending())
            .build();

    return this.cypherRenderer.render(chunkStatement);
  }

  @VisibleForTesting
  String buildNodeHybridSearchCypher() {
    return """
        WITH $queryVector AS qv,
             $searchQuery AS kq,
             $topK AS limitK,
             $rrfConstant AS rrfConstant,
             toFloat($weightFulltext) AS wFt,
             toFloat($weightVector) AS wVt
        CALL {WITH kq, limitK, rrfConstant, wFt
        CALL db.index.fulltext.queryNodes('node_fulltext', kq, {limit: limitK})
        YIELD node, score
        WITH node, score, rrfConstant, wFt
        ORDER BY score DESC, node.id ASC
        WITH collect(node) AS nodes, rrfConstant, wFt
        UNWIND range(0, (size(nodes) - 1)) AS rank
        RETURN
          nodes[rank] AS node,
          (wFt / ((rrfConstant + rank) + 1.0)) AS score

        UNION ALL

        WITH qv, limitK, rrfConstant, wVt
        CALL db.index.vector.queryNodes('node_embedding', limitK, qv)
        YIELD node, score
        WITH node, score, rrfConstant, wVt
        ORDER BY score DESC, node.id ASC
        WITH collect(node) AS nodes, rrfConstant, wVt
        UNWIND range(0, (size(nodes) - 1)) AS rank
        RETURN
          nodes[rank] AS node,
          (wVt / ((rrfConstant + rank) + 1.0)) AS score}
        WITH node AS anchor, sum(score) AS wrrf
        ORDER BY wrrf DESC, node.id ASC
        LIMIT $topK
        CALL {WITH anchor
          OPTIONAL MATCH p_struct = (anchor)-[:%s*1..2]->(target_inv:Node)
          WITH p_struct, target_inv
          WHERE p_struct IS NOT NULL
          ORDER BY
            CASE WHEN 'Observation' IN labels(target_inv) THEN 1 ELSE 2 END ASC,
            length(p_struct) ASC
          LIMIT $topTraversal
          RETURN collect(p_struct) AS valid_paths}
        RETURN coalesce(anchor.id, elementId(anchor)) as anchor_id,
               anchor.title as anchor_title,
               labels(anchor) AS anchor_labels,
               apoc.map.clean(properties(anchor), ['embedding', 'title', 'id'], []) AS anchor_props,
               wrrf,
               [p IN valid_paths | {
                nodes: [n IN nodes(p) | {id: n.id, title: n.title, labels: labels(n), props: apoc.map.clean(properties(n), ['embedding', 'title', 'id'], [])}],
                edges: [rel IN relationships(p) | {type: type(rel), desc: rel.description}]
                }] AS structural_paths
        ORDER BY wrrf DESC, anchor_id ASC
        """
        .formatted(TRAVERSAL_REL_CYPHER);
  }

  @VisibleForTesting
  String buildRelationshipSemanticSearchCypher() {
    return """
        WITH $queryVector AS qv,
             $topK AS limitK,
             $guardrail AS threshold
        CALL db.index.vector.queryRelationships('related_to_embedding', limitK, qv)
        YIELD relationship AS rel, score
        WHERE score > threshold
        WITH rel, score, startNode(rel) AS src, endNode(rel) AS tgt
        CALL {WITH src
        OPTIONAL MATCH (src)-[:`HAS_OBSERVATION`]->(src_obs:`Observation`)
        RETURN collect(src_obs { .*, embedding: null }) AS src_observations}
        CALL {WITH tgt
        OPTIONAL MATCH (tgt)-[:`HAS_OBSERVATION`]->(tgt_obs:`Observation`)
        RETURN collect(tgt_obs { .*, embedding: null }) AS tgt_observations}
        RETURN type(rel) AS edge_type,
               rel.description AS edge_desc,
               score AS semantic_score,
               {id: coalesce(src.id, elementId(src)),
                 title: src.title,
                 labels: labels(src),
                 props: apoc.map.clean(properties(src), ['embedding', 'title', 'id'], []),
                 observations: src_observations} AS source_node,
               {id: coalesce(tgt.id, elementId(tgt)),
                title: tgt.title,
                labels: labels(tgt),
                props: apoc.map.clean(properties(tgt), ['embedding', 'title', 'id'], []),
                observations: tgt_observations} AS target_node
    """;
  }
}
