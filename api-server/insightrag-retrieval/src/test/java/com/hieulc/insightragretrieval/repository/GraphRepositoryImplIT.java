package com.hieulc.insightragretrieval.repository;

import static com.hieulc.insightragretrieval.helper.StreamHelper.resourceToString;
import static org.assertj.core.api.Assertions.assertThat;

import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.springframework.boot.autoconfigure.MigrationsAutoConfiguration;
import com.hieulc.insightragretrieval.base.AbstractNeo4jIT;
import com.hieulc.insightragretrieval.config.properties.GraphSearchProperties;
import com.hieulc.insightragretrieval.dto.context.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.neo4j.test.autoconfigure.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataNeo4jTest
@ActiveProfiles("test")
@Import({GraphRepositoryImpl.class, GraphContextMapper.class})
@EnableConfigurationProperties(GraphSearchProperties.class)
@ImportAutoConfiguration(MigrationsAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GraphRepositoryImplIT extends AbstractNeo4jIT {

  @Autowired private GraphCustomRepository repository;
  @Autowired private Neo4jClient neo4jClient;
  @Autowired private GraphContextMapper rowMapper;
  @Autowired private GraphSearchProperties graphSearchProperties;

  @Value("${app.test.neo4j.test-fixtures}")
  private Resource seedDataCypher;

  @Autowired private Migrations migrations;

  @BeforeEach
  void setUp() throws Exception {
    migrations.apply();
    neo4jClient.query(resourceToString(seedDataCypher)).run();
    neo4jClient.query("CALL db.awaitIndexes(10)").run();
  }

  @AfterEach
  void cleanUp() {
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();
  }

  @Test
  void searchChunksHybrid_aggregates_chunk_context_by_keyword_with_non_related_vector() {
    float[] vector = generateDummyVector(-0.5f);
    List<DocumentChunkContext> results = repository.searchChunksHybrid(vector, "Entity", 1);

    assertThat(results).isNotEmpty();
    assertThat(results).hasSize(1);
    DocumentChunkContext chunkContext = results.getFirst();

    assertThat(chunkContext.chunkId()).isEqualTo("c1");
    assertThat(chunkContext.text()).isEqualTo("Chunk text is an Object and Entity");
    assertThat(chunkContext.metadata().documentId()).isEqualTo("test-document");
    assertThat(chunkContext.metadata().pageNumber()).isEqualTo(1);
  }

  @Test
  void searchChunksHybrid_aggregates_chunk_context_by_vector_with_non_related_keyword() {
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
  }

  @Test
  void searchNodeHybrid_aggregates_node_context_by_keyword_with_non_related_vector() {
    float[] vector = generateDummyVector(-0.3f);
    List<DocumentNodeContext> results = repository.searchNodeHybrid(vector, "Entity", 1);

    assertThat(results).isNotEmpty();
    assertThat(results).hasSize(1);
    DocumentNodeContext nodeContext = results.getFirst();

    DocumentNode documentNode = nodeContext.documentNode();

    assertThat(documentNode.id()).isEqualTo("node-1");
    assertThat(documentNode.title()).isEqualTo("Entity-1");
    assertThat(documentNode.labels().containsAll(List.of("Node", "Entity")));
    assertThat(documentNode.properties()).containsKeys("embedding", "title", "id");

    assertThat(nodeContext.relatedEntities()).hasSize(2);
    DocumentNodeContext.RelatedNodesContext firstConnection =
        nodeContext.relatedEntities().getFirst();
    assertThat(firstConnection.source().id()).isEqualTo("node-1");
    assertThat(firstConnection.source().title()).isEqualTo("Entity-1");
    assertThat(firstConnection.edge().edgeType()).isEqualTo("HAS_OBSERVATION");
    assertThat(firstConnection.target().id()).isEqualTo("obs-2");
    assertThat(firstConnection.target().title()).isNull();
    assertThat(firstConnection.target().labels()).contains("Observation");
    assertThat(firstConnection.target().properties())
        .containsEntry("value", "prop")
        .containsEntry("is_numeric", false);

    DocumentNodeContext.RelatedNodesContext lastConnection =
        nodeContext.relatedEntities().getLast();
    assertThat(lastConnection.source().id()).isEqualTo("node-1");
    assertThat(lastConnection.source().title()).isEqualTo("Entity-1");
    assertThat(lastConnection.edge().edgeType()).isEqualTo("COMPONENT_OF");
    assertThat(lastConnection.target().id()).isEqualTo("node-2");
    assertThat(lastConnection.target().title()).isEqualTo("Subject-1");
    assertThat(lastConnection.target().labels()).contains("Subject");

    assertThat(nodeContext.sourceChunks()).hasSize(1);
    DocumentChunkContext chunkContext = nodeContext.sourceChunks().getFirst();
    assertThat(chunkContext.chunkId()).isEqualTo("c1");
    assertThat(chunkContext.text()).isEqualTo("");
    assertThat(chunkContext.metadata().documentId()).isEqualTo("test-document");
    assertThat(chunkContext.metadata().pageNumber()).isEqualTo(1);
  }

  @Test
  void searchNodeHybrid_aggregates_node_context_by_vector_with_non_related_keyword() {
    float[] vector = generateDummyVector(-0.3f);
    List<DocumentNodeContext> results =
        repository.searchNodeHybrid(vector, "non-exists-keyword", 1);

    assertThat(results).isNotEmpty();
    assertThat(results).hasSize(1);
    DocumentNodeContext nodeContext = results.getFirst();

    DocumentNode documentNode = nodeContext.documentNode();

    assertThat(documentNode.id()).isEqualTo("node-2");
    assertThat(documentNode.title()).isEqualTo("Subject-1");
    assertThat(documentNode.labels().containsAll(List.of("Node", "Subject")));

    assertThat(nodeContext.relatedEntities()).hasSize(2);

    DocumentNodeContext.RelatedNodesContext firstConnection =
        nodeContext.relatedEntities().getFirst();
    assertThat(firstConnection.source().id()).isEqualTo("node-2");
    assertThat(firstConnection.source().title()).isEqualTo("Subject-1");
    assertThat(firstConnection.edge().edgeType()).isEqualTo("HAS_OBSERVATION");
    assertThat(firstConnection.target().id()).isEqualTo("obs-1");
    assertThat(firstConnection.target().title()).isNull();
    assertThat(firstConnection.target().labels()).contains("Observation");
    assertThat(firstConnection.target().properties())
        .containsEntry("value", 123.0)
        .containsEntry("is_numeric", true);

    DocumentNodeContext.RelatedNodesContext lastConnection =
        nodeContext.relatedEntities().getLast();
    assertThat(lastConnection.source().id()).isEqualTo("node-1");
    assertThat(lastConnection.source().title()).isEqualTo("Entity-1");
    assertThat(lastConnection.edge().edgeType()).isEqualTo("COMPONENT_OF");
    assertThat(lastConnection.target().id()).isEqualTo("node-2");
    assertThat(lastConnection.target().title()).isEqualTo("Subject-1");
    assertThat(lastConnection.target().labels()).contains("Subject");

    assertThat(nodeContext.sourceChunks()).hasSize(1);
    DocumentChunkContext chunkContext = nodeContext.sourceChunks().getFirst();
    assertThat(chunkContext.chunkId()).isEqualTo("c2");
    assertThat(chunkContext.text()).isEqualTo("");
    assertThat(chunkContext.metadata().documentId()).isEqualTo("test-document");
    assertThat(chunkContext.metadata().pageNumber()).isEqualTo(2);
  }

  @Test
  void
      searchRelationshipSemantic_aggregates_relationship_context_by_vector_search_within_threshold() {
    float[] vector = generateDummyVector(0.1f);
    List<DocumentRelationshipContext> results = repository.searchRelationshipSemantic(vector, 1);

    assertThat(results).isNotEmpty();
    assertThat(results).hasSize(1);
    DocumentRelationshipContext relationshipContext = results.getFirst();

    assertThat(relationshipContext.nodeEdge().edgeType()).isEqualTo("RELATED_TO");
    assertThat(relationshipContext.nodeEdge().edgeDesc())
        .contains("Subject is a component of Entity");

    assertThat(relationshipContext.sourceNode().id()).isEqualTo("node-2");
    assertThat(relationshipContext.sourceNode().labels()).contains("Subject");

    assertThat(relationshipContext.targetNode().id()).isEqualTo("node-1");
    assertThat(relationshipContext.targetNode().labels()).contains("Entity");
  }

  @Test
  void searchRelationshipSemantic_empty_result_when_threshold_exceeded() {
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
