package com.hieulc.insightragingestion.service.graph;

import static com.hieulc.insightragingestion.factory.GraphTestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import com.hieulc.insightragingestion.base.AbstractNeo4jIT;
import com.hieulc.insightragingestion.dto.DocumentChunk;
import com.hieulc.insightragingestion.dto.DocumentGraph;
import com.hieulc.insightragingestion.dto.Entity;
import com.hieulc.insightragingestion.dto.Relationship;
import com.hieulc.insightragingestion.repository.GraphCustomRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class GraphIngestionServiceIT extends AbstractNeo4jIT {

  @Autowired private GraphIngestionService ingestionService;

  @MockitoSpyBean private GraphCustomRepository graphCustomRepository;

  @Autowired private Neo4jClient neo4jClient;

  private static final String docId = "document-test";

  @AfterEach
  void cleanDb() {
    neo4jClient.query("MATCH (n:DocumentChunk|Document|Node) DETACH DELETE n").run();
  }

  @Test
  @DisplayName("Should rollback entire graph transactions if relationship insert fails")
  void shouldRollbackEntireTransaction_WhenOneTransactionFails() {
    DocumentChunk chunk = createDefaultChunk("c1", docId);
    Entity e1 = createEntity("e1", null, Set.of("c1"));
    Entity e2 = createEntity("e2", null, Set.of("c1"));
    Relationship relationship = createRelationship("e1", "e2", "RELATED_TO", Set.of("c1"), 1.0);
    DocumentGraph graph =
        new DocumentGraph(docId, List.of(chunk), List.of(e1, e2), List.of(relationship));

    doThrow(new RuntimeException("Network timeout"))
        .when(graphCustomRepository)
        .batchInsertRelationship(anyList());

    assertThatThrownBy(() -> ingestionService.ingest(graph))
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
