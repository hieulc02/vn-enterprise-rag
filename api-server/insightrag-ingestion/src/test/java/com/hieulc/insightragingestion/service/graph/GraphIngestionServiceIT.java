package com.hieulc.insightragingestion.service.graph;

import static com.hieulc.insightragingestion.factory.GraphTestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import com.hieulc.insightragingestion.base.AbstractNeo4jIT;
import com.hieulc.insightragingestion.dto.graph.DocumentChunk;
import com.hieulc.insightragingestion.dto.graph.DocumentGraph;
import com.hieulc.insightragingestion.dto.graph.Entity;
import com.hieulc.insightragingestion.dto.graph.Relationship;
import com.hieulc.insightragingestion.repository.graph.GraphCustomRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.neo4j.test.autoconfigure.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataNeo4jTest
@Import(GraphIngestionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GraphIngestionServiceIT extends AbstractNeo4jIT {

  @Autowired private GraphIngestionService ingestionService;

  @MockitoBean private GraphCustomRepository graphCustomRepository;

  @Autowired private Neo4jClient neo4jClient;

  private static final String DOCUMENT_ID = "document-test";

  @AfterEach
  void cleanDb() {
    neo4jClient.query("MATCH (n:DocumentChunk|Document|Node) DETACH DELETE n").run();
  }

  @Test
  @DisplayName("Should rollback entire graph transactions if relationship insert fails")
  void ingest_rolls_back_entire_transaction_when_one_transaction_fails() {
    DocumentChunk chunk = createDefaultChunk("c1", DOCUMENT_ID);
    Entity e1 = createEntity("e1", null, Set.of("c1"));
    Entity e2 = createEntity("e2", null, Set.of("c1"));
    Relationship relationship = createRelationship("e1", "e2", "RELATED_TO", Set.of("c1"), 1.0);
    DocumentGraph graph =
        new DocumentGraph(DOCUMENT_ID, List.of(chunk), List.of(e1, e2), List.of(relationship));

    doThrow(new RuntimeException("Network timeout"))
        .when(graphCustomRepository)
        .batchInsertRelationship(anyList());

    assertThatThrownBy(() -> ingestionService.ingest(DOCUMENT_ID, graph))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Network timeout");

    assertThat(totalTransactionNode()).isEqualTo(0L);
  }

  private Long totalTransactionNode() {
    return neo4jClient
        .query("MATCH (n:DocumentChunk|Document|Node) RETURN count(n)")
        .fetchAs(Long.class)
        .one()
        .orElse(0L);
  }
}
