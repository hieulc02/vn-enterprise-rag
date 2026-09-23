package com.hieulc.insightragretrieval.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.cypherdsl.core.renderer.Renderer;

class GraphRepositoryImplTest {

  private GraphRepositoryImpl graphRepository;

  @BeforeEach
  void setUp() {
    Configuration cypherDslConfiguration =
        Configuration.newConfig().withDialect(Dialect.NEO4J_5).build();
    Renderer cypherRenderer = Renderer.getRenderer(cypherDslConfiguration);

    graphRepository = new GraphRepositoryImpl(null, cypherRenderer);
  }

  @Test
  void shouldGenerateChunkHybridSearchCypherCorrectly() {
    String actualCypher = graphRepository.buildChunkHybridSearchCypher();

    String expectedChunk =
        """
        WITH $queryVector AS qv,
             $searchQuery AS kq,
             $topK AS limitK,
             $rrfConstant AS rrfConstant,
             toFloat($weightFulltext) AS wFt,
             toFloat($weightVector) AS wVt
        CALL {WITH kq, limitK, rrfConstant, wFt
            CALL db.index.fulltext.queryNodes('chunk_fulltext', kq, {limit: limitK})
            YIELD node, score
            WITH node, score, rrfConstant, wFt
            ORDER BY score DESC, node.chunk_id ASC
            WITH collect(node) AS nodes, rrfConstant, wFt
            UNWIND range(0, (size(nodes) - 1)) AS rank
            RETURN
              nodes[rank] AS node,
              (wFt / ((rrfConstant + rank) + 1.0)) AS score

            UNION ALL

            WITH qv, limitK, rrfConstant, wVt
            CALL db.index.vector.queryNodes('chunk_embedding', limitK, qv)
            YIELD node, score
            WITH node, score, rrfConstant, wVt
            ORDER BY score DESC, node.chunk_id ASC
            WITH collect(node) AS nodes, rrfConstant, wVt
            UNWIND range(0, (size(nodes) - 1)) AS rank
            RETURN
              nodes[rank] AS node,
              (wVt / ((rrfConstant + rank) + 1.0)) AS score}
            WITH node, sum(score) AS wrrf, limitK
            ORDER BY wrrf DESC, node.chunk_id ASC
            LIMIT $topK
            CALL {WITH node OPTIONAL MATCH (src:`Node`)-[:`EXTRACTED_FROM`]->(node)
            RETURN collect(DISTINCT src.id) AS linked_entities}
            RETURN node.chunk_id AS chunk_id,
               node.text AS text,
               {document_id: node.document_id,
                 page_number: node.page_number} AS metadata,
               linked_entities,
               wrrf
            ORDER BY wrrf DESC, chunk_id ASC
        """;

    assertThat(actualCypher).isEqualToNormalizingWhitespace(expectedChunk);
  }

  @Test
  void shouldGenerateNodeHybridSearchCypher() {
    String actualCypher = graphRepository.buildNodeHybridSearchCypher();
    String expectedChunk =
        """
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
              OPTIONAL MATCH p_struct = (anchor)-[:OWNS|INVESTS_IN|COMPONENT_OF|AFFECTED_BY|REPORTED_BY|IS_CHILD_OF|HAS_OBSERVATION|HAS_VALUE*1..2]->(target_inv:Node)
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
        """;

    assertThat(actualCypher).isEqualToNormalizingWhitespace(expectedChunk);
  }

  @Test
  void shouldGenerateAmbientRelationshipContextCorrectly() {
    String actualCypher = graphRepository.buildRelationshipSemanticSearchCypher();

    String expectedCypher =
        """
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

    assertThat(actualCypher).isEqualToNormalizingWhitespace(expectedCypher);
  }
}
