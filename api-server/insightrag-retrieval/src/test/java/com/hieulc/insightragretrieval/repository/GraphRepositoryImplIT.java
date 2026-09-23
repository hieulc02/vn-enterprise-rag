package com.hieulc.insightragretrieval.repository;

import static com.hieulc.insightragretrieval.helper.StreamHelper.resourceToString;
import static org.assertj.core.api.Assertions.assertThat;

import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.springframework.boot.autoconfigure.MigrationsAutoConfiguration;
import com.hieulc.insightragretrieval.base.AbstractNeo4jIT;
import com.hieulc.insightragretrieval.config.CypherDslConfig;
import com.hieulc.insightragretrieval.dto.context.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.neo4j.test.autoconfigure.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataNeo4jTest
@ActiveProfiles("test")
@Import({CypherDslConfig.class, GraphRepositoryImpl.class})
@ImportAutoConfiguration(MigrationsAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GraphRepositoryImplIT extends AbstractNeo4jIT {

  @Autowired private GraphCustomRepository repository;

  @Autowired private Neo4jClient neo4jClient;

  @Value("${app.test.neo4j.test-fixtures}")
  private Resource seedDataCypher;

  @Autowired private Migrations migrations;

  @BeforeEach
  void setUp() throws Exception {
    migrations.apply();
    neo4jClient.query(resourceToString(seedDataCypher)).run();
    neo4jClient.query("CALL db.awaitIndexes(30)").run();
  }

  @AfterEach
  void cleanUp() {
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();
  }

  @Test
  void searchChunkFulltext_ReturnAndMapPropertiesCorrectlyAndHandleLinkedEntities() {
    float[] vector = generateDummyVector(-0.3f);
    List<DocumentChunkContext> results = repository.searchChunksHybrid(vector, "Entity", 1);

    assertThat(results).isNotEmpty();
    assertThat(results).hasSize(1);
    DocumentChunkContext chunkContext = results.getFirst();

    assertThat(chunkContext.chunkId()).isEqualTo("c1");
    assertThat(chunkContext.text()).isEqualTo("Chunk text is an Object and Entity");
    assertThat(chunkContext.metadata().documentId()).isEqualTo("test-document");
    assertThat(chunkContext.metadata().pageNumber()).isEqualTo(1);
    assertThat(chunkContext.linkedEntities()).contains("node-1");
  }

  @Test
  void searchChunkVector_ReturnAndMapPropertiesCorrectlyAndHandleLinkedEntities() {
    float[] vector = generateDummyVector(0.1f);
    List<DocumentChunkContext> results =
        repository.searchChunksHybrid(vector, "non-exists-keyword", 1);

    assertThat(results).isNotEmpty();
    assertThat(results).hasSize(1);
    DocumentChunkContext chunkContext = results.getFirst();

    assertThat(chunkContext.chunkId()).isEqualTo("c1");
    assertThat(chunkContext.text()).isEqualTo("Chunk text is an Object and Entity");
    assertThat(chunkContext.metadata().documentId()).isEqualTo("test-document");
    assertThat(chunkContext.metadata().pageNumber()).isEqualTo(1);
    assertThat(chunkContext.linkedEntities()).contains("node-1");
  }

  @Test
  void searchNodeFulltext_HandleMultiHopInCorrectOrderAndStripEmbeddings() {
    float[] vector = generateDummyVector(-0.3f);
    List<DocumentNodeContext> results = repository.searchNodeHybrid(vector, "Entity", 1);

    assertThat(results).isNotEmpty();
    assertThat(results).hasSize(1);
    DocumentNodeContext nodeContext = results.getFirst();

    DocumentNode documentNode = nodeContext.documentNode();

    assertThat(documentNode.id()).isEqualTo("node-1");
    assertThat(documentNode.title()).isEqualTo("Entity-1");
    assertThat(documentNode.labels().containsAll(List.of("Node", "Entity")));
    assertThat(documentNode.properties()).doesNotContainKeys("embedding", "title", "id");

    assertThat(nodeContext.structuralPaths()).hasSize(2);
    List<DocumentNode> nodeObs = nodeContext.structuralPaths().getFirst().nodes();
    assertThat(nodeObs).hasSize(3);
    assertThat(nodeObs.get(0).labels()).contains("Entity");
    assertThat(nodeObs.get(1).labels()).contains("Subject");
    assertThat(nodeObs.get(2).labels()).contains("Observation");

    List<NodeEdge> edgesToObs = nodeContext.structuralPaths().getFirst().edges();
    assertThat(edgesToObs).hasSize(2);
    assertThat(edgesToObs.get(0).edgeType()).contains("COMPONENT_OF");
    assertThat(edgesToObs.get(1).edgeType()).contains("HAS_OBSERVATION");

    List<DocumentNode> nodeSubject = nodeContext.structuralPaths().getLast().nodes();
    assertThat(nodeSubject).hasSize(2);
    assertThat(nodeSubject.get(0).labels()).contains("Entity");
    assertThat(nodeSubject.get(1).labels()).contains("Subject");

    List<NodeEdge> edgesToSub = nodeContext.structuralPaths().getLast().edges();
    assertThat(edgesToSub).hasSize(1);
    assertThat(edgesToSub.getFirst().edgeType()).contains("COMPONENT_OF");
  }

  @Test
  void searchNodeVector_HandleMultiHopInCorrectOrderAndStripEmbeddings() {
    float[] vector = generateDummyVector(0.1f);
    List<DocumentNodeContext> results =
        repository.searchNodeHybrid(vector, "non-exists-keyword", 1);

    assertThat(results).isNotEmpty();
    assertThat(results).hasSize(1);
    DocumentNodeContext nodeContext = results.getFirst();

    DocumentNode documentNode = nodeContext.documentNode();

    assertThat(documentNode.id()).isEqualTo("node-1");
    assertThat(documentNode.title()).isEqualTo("Entity-1");
    assertThat(documentNode.labels().containsAll(List.of("Node", "Entity")));
    assertThat(documentNode.properties()).doesNotContainKeys("embedding", "title", "id");

    assertThat(nodeContext.structuralPaths()).hasSize(2);
    List<DocumentNode> nodeObs = nodeContext.structuralPaths().getFirst().nodes();
    assertThat(nodeObs).hasSize(3);
    assertThat(nodeObs.get(0).labels()).contains("Entity");
    assertThat(nodeObs.get(1).labels()).contains("Subject");
    assertThat(nodeObs.get(2).labels()).contains("Observation");

    List<NodeEdge> edgesToObs = nodeContext.structuralPaths().getFirst().edges();
    assertThat(edgesToObs).hasSize(2);
    assertThat(edgesToObs.get(0).edgeType()).contains("COMPONENT_OF");
    assertThat(edgesToObs.get(1).edgeType()).contains("HAS_OBSERVATION");

    List<DocumentNode> nodeSubject = nodeContext.structuralPaths().getLast().nodes();
    assertThat(nodeSubject).hasSize(2);
    assertThat(nodeSubject.get(0).labels()).contains("Entity");
    assertThat(nodeSubject.get(1).labels()).contains("Subject");

    List<NodeEdge> edgesToSub = nodeContext.structuralPaths().getLast().edges();
    assertThat(edgesToSub).hasSize(1);
    assertThat(edgesToSub.getFirst().edgeType()).contains("COMPONENT_OF");
  }

  @Test
  void searchRelationshipSemantic_YieldAndMapPropertiesWithMultiHop() {
    float[] vector = generateDummyVector(0.1f);
    List<DocumentRelationshipContext> results = repository.searchRelationshipSemantic(vector, 1);

    assertThat(results).isNotEmpty();
    assertThat(results).hasSize(1);
    DocumentRelationshipContext relationshipContext = results.getFirst();

    assertThat(relationshipContext.nodeEdge().edgeType()).isEqualTo("RELATED_TO");
    assertThat(relationshipContext.nodeEdge().edgeDesc())
        .contains("Subject is a component of Entity");

    assertThat(relationshipContext.sourceNode().nodeMetadata().id()).isEqualTo("node-2");
    assertThat(relationshipContext.sourceNode().nodeMetadata().labels()).contains("Subject");
    assertThat(relationshipContext.sourceNode().nodeMetadata().properties())
        .doesNotContainKeys("embedding", "title", "id");

    assertThat(relationshipContext.sourceNode().obsPropsList()).hasSize(1);
    assertThat(relationshipContext.sourceNode().obsPropsList().getFirst())
        .containsEntry("value", 123.0);

    assertThat(relationshipContext.targetNode().nodeMetadata().id()).isEqualTo("node-1");
    assertThat(relationshipContext.targetNode().nodeMetadata().title()).contains("Entity");
    assertThat(relationshipContext.targetNode().obsPropsList()).isEmpty();
  }

  @Test
  void searchRelationshipSemantic_YieldNoRelationship_WhenSimilarityScoreBelowThreshold() {
    float[] vector = generateDummyVector(-0.2f);
    List<DocumentRelationshipContext> results = repository.searchRelationshipSemantic(vector, 2);
    assertThat(results).isEmpty();
  }

  private float[] generateDummyVector(float value) {
    float[] vector = new float[1024];
    Arrays.fill(vector, value);
    return vector;
  }
}
