package com.hieulc.insightragingestion.repository;

import static com.hieulc.insightragingestion.factory.GraphTestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.hieulc.insightragingestion.base.AbstractNeo4jIT;
import com.hieulc.insightragingestion.dto.DocumentChunk;
import com.hieulc.insightragingestion.dto.Entity;
import com.hieulc.insightragingestion.dto.Relationship;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.neo4j.test.autoconfigure.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;

@DataNeo4jTest
@Import(GraphRepositoryImpl.class)
class GraphRepositoryIT extends AbstractNeo4jIT {

  @Autowired private GraphCustomRepository repository;

  @Autowired private Neo4jClient neo4jClient;

  @AfterEach
  void cleanDb() {
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();
  }

  @Test
  void shouldCreateDocumentAndMultipleChunksInSingleBatch() {
    String docId = "test-document";
    var chunks = generateListChunks(docId, 2);

    repository.batchInsertChunks(docId, chunks);

    long docCount = countNode("Document");
    assertThat(docCount).isEqualTo(1);

    long chunkCount = countNode("DocumentChunk");
    assertThat(chunkCount).isEqualTo(2);

    long edgeCount =
        neo4jClient
            .query(
                "MATCH (c:DocumentChunk)-[r:PART_OF]->(d:Document {document_id: $docId}) RETURN count(r)")
            .bind(docId)
            .to("docId")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
    assertThat(edgeCount).isEqualTo(2);
  }

  @Test
  void shouldBeIdempotent_WhenDoubleMergeWithTheSameChunk() {
    String docId = "test-document";
    var chunk = createDefaultChunk("c1", docId);

    repository.batchInsertChunks(docId, List.of(chunk));
    repository.batchInsertChunks(docId, List.of(chunk));

    assertThat(countNode("Document")).isEqualTo(1);
    assertThat(countNode("DocumentChunk")).isEqualTo(1);

    long edgeCount =
        neo4jClient
            .query("MATCH (c:DocumentChunk)-[r:PART_OF]->(d:Document) RETURN count(r)")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
    assertThat(edgeCount).isEqualTo(1);
  }

  @Test
  void shouldUpdateExistingChunk_WhenPropertiesChanged() {
    String docId = "test-document";
    var initChunk = createChunk("c1", docId, "INIT-TEXT", 1);
    repository.batchInsertChunks(docId, List.of(initChunk));

    var updatedChunk = createChunk("c1", docId, "UPDATED-TEXT", 2);
    repository.batchInsertChunks(docId, List.of(updatedChunk));

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) getChunkProperties("c1").get("props");

    assertThat(props).containsEntry("text", "UPDATED-TEXT").containsEntry("chunk_index", 2L);
  }

  @Test
  void shouldFlattenChunkMetadata() {
    String docId = "test-document";
    var chunk = createChunk("c2", docId, "FLATTEN-TEXT", 22);
    repository.batchInsertChunks(docId, List.of(chunk));

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) getChunkProperties("c2").get("props");

    assertThat(props).containsEntry("document_id", docId).containsEntry("page_number", 22L);
  }

  @Test
  void shouldHandleNullChunkMetadataGracefully() {
    String docId = "test-document";
    var chunk = new DocumentChunk("c3", "METADATA-EMPTY", null, 0, new float[] {});
    repository.batchInsertChunks(docId, List.of(chunk));

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) getChunkProperties("c3").get("props");
    assertThat(props).containsEntry("text", "METADATA-EMPTY");
    assertThat(props.containsKey("page_number")).isFalse();
  }

  @Test
  void shouldInsertEntities_WithFlattenedPropertiesAndAliases() {
    var aliases = List.of("Alias 1", "Alias 2");
    var entity = createEntity("e1", aliases, null);

    repository.batchInsertEntities(List.of(entity));

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) getEntityProperties("e1").get("props");
    assertThat(props)
        .containsEntry("id", "e1")
        .containsEntry("title", "Default-Title")
        .containsEntry("attribute", "fake");

    @SuppressWarnings("unchecked")
    List<String> entityAliases = (List<String>) props.get("aliases");
    assertThat(entityAliases).containsExactlyElementsOf(aliases);
  }

  @Test
  void shouldUpdateEntityProps_WhenSendDuplicatedEntity() {
    var initialEntity = createEntity("e2", null, null);
    repository.batchInsertEntities(List.of(initialEntity));

    var updatedEntity =
        new Entity(
            "e2",
            "Updated-Title",
            null,
            List.of(),
            "Updated-Description",
            null,
            Map.of("new_prop", "value"));

    repository.batchInsertEntities(List.of(updatedEntity));

    long entityCount = countNode("Entity");
    assertThat(entityCount).isEqualTo(1L);

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) getEntityProperties("e2").get("props");

    assertThat(props)
        .containsEntry("title", "Updated-Title")
        .containsEntry("description", "Updated-Description")
        .containsEntry("new_prop", "value")
        .containsEntry("attribute", "fake");
  }

  @Test
  void shouldCreateEntityEdgeLinkingWithDocumentChunk() {
    createDummyDocumentChunk("chunk-1");
    createDummyDocumentChunk("chunk-2");

    var entity = createEntity("e1", null, Set.of("chunk-1", "chunk-2"));
    repository.batchInsertEntities(List.of(entity));

    long edgeCount =
        neo4jClient
            .query(
                "MATCH (e:Entity {id: 'e1'})-[r:EXTRACTED_FROM]->(c:DocumentChunk) RETURN count(r)")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);

    assertThat(edgeCount).isEqualTo(2L);
  }

  @Test
  void returnNoEntityEdgeCreatedWhenSourceChunkIdsEmptyOrNull() {

    var entityWithEmptyChunks = createEntity("e1", null, Set.of());
    var entityWithNullChunks = createEntity("e2", null, null);

    repository.batchInsertEntities(List.of(entityWithEmptyChunks, entityWithNullChunks));

    long edgeCount = countRelationships("EXTRACTED_FROM");

    assertThat(edgeCount).isEqualTo(0L);
  }

  @Test
  void shouldInsertRelationships_AndGroupByType() {

    createDummyEntity("e1");
    createDummyEntity("e2");
    createDummyEntity("e3");

    var rel1 = createRelationship("e1", "e2", "RELATED_TO", null, 1.0);
    var rel2 = createRelationship("e2", "e3", "COMPONENT_OF", null, 1.0);

    repository.batchInsertRelationship(List.of(rel1, rel2));

    long relatedCount = countRelationships("RELATED_TO");
    long componentCount = countRelationships("COMPONENT_OF");

    assertThat(relatedCount).isEqualTo(1L);
    assertThat(componentCount).isEqualTo(1L);
  }

  @Test
  void shouldMapAndFlattenRelationshipPropertiesCorrectly() {

    createDummyEntity("e1");
    createDummyEntity("e2");

    var relationship =
        new Relationship(
            "rel-id",
            "e1",
            "e2",
            "RELATED_TO",
            "RELATIONSHIP-DES",
            1.0,
            Set.of("chunk-1", "chunk-2"),
            Map.of("props", "value"));

    repository.batchInsertRelationship(List.of(relationship));

    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) getRelationshipProperties("e1", "e2", "RELATED_TO").get("props");

    assertThat(props)
        .containsEntry("description", "RELATIONSHIP-DES")
        .containsEntry("weight", 1.0)
        .containsEntry("props", "value");

    @SuppressWarnings("unchecked")
    List<String> chunkIds = (List<String>) props.get("source_chunk_ids");

    assertThat(chunkIds).containsExactlyInAnyOrder("chunk-1", "chunk-2");
  }

  @Test
  void shouldBeIdempotent_AndUpdateRelationshipProps() {
    createDummyEntity("e1");
    createDummyEntity("e2");

    var initRelationship = createRelationship("e1", "e2", "RELATED_TO", null, 1.0);
    repository.batchInsertRelationship(List.of(initRelationship));
    var updateRelationship = createRelationship("e1", "e2", "RELATED_TO", null, 0.9);
    repository.batchInsertRelationship(List.of(updateRelationship));

    assertThat(countRelationships("RELATED_TO")).isEqualTo(1L);

    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) getRelationshipProperties("e1", "e2", "RELATED_TO").get("props");
    assertThat(props).containsEntry("weight", 0.9);
  }

  @Test
  void shouldIgnoreRelationship_IfSourceOrTargetNodeIsMissing() {
    createDummyEntity("e1");

    var orphanRelationship = createRelationship("e1", "missing-target", "RELATED_TO", null, 1.0);

    repository.batchInsertRelationship(List.of(orphanRelationship));

    long orphanCount = countRelationships("RELATED_TO");
    assertThat(orphanCount).isEqualTo(0L);
  }

  @Test
  void shouldLinkChunksInAscendingOrder() {
    String docId = "test-document";
    seedChunk(docId, "c1", 1);
    seedChunk(docId, "c3", 3);
    seedChunk(docId, "c2", 2);
    seedChunk(docId, "c4", 4);

    int chunkCount = repository.linkChunks(docId);

    assertThat(chunkCount).isEqualTo(4);

    assertThat(hasNextEdge("c1", "c2")).isEqualTo(true);
    assertThat(hasNextEdge("c2", "c3")).isEqualTo(true);
    assertThat(hasNextEdge("c3", "c4")).isEqualTo(true);
    assertThat(hasNextEdge("c4", "c1")).isEqualTo(false);

    long edgeCount = countRelationships("NEXT");
    assertThat(edgeCount).isEqualTo(3L);
  }

  @Test
  void shouldBeIdempotent_WhenMergeDuplicateDocument() {
    String docId = "test-document";
    seedChunk(docId, "c1", 1);
    seedChunk(docId, "c2", 2);

    repository.linkChunks(docId);
    repository.linkChunks(docId);

    long edgeCount = countRelationships("NEXT");
    assertThat(edgeCount).isEqualTo(1L);
  }

  @Test
  void shouldHandleNonExistingDocumentGracefully() {
    repository.linkChunks("non-existing-document");

    long edgeCount = countRelationships("NEXT");
    assertThat(edgeCount).isEqualTo(0);
  }

  @Test
  void shouldHandleSingleChunkGracefully() {
    String docId = "test-document";
    seedChunk(docId, "c1", 1);

    int singleChunkCount = repository.linkChunks(docId);

    assertThat(singleChunkCount).isEqualTo(0);
    assertThat(hasNextEdge("c1", "c1")).isFalse();
    assertThat(countRelationships("NEXT")).isEqualTo(0L);
  }

  private void createDummyEntity(String id) {
    neo4jClient.query("CREATE (:Entity {id: $id})").bind(id).to("id").run();
  }

  private void createDummyDocumentChunk(String chunkId) {
    neo4jClient.query("CREATE (d:DocumentChunk {chunk_id: $id})").bind(chunkId).to("id").run();
  }

  private void seedChunk(String docId, String chunkId, int index) {
    neo4jClient
        .query(
            """
                   MERGE (d:Document {document_id: $docId})
                   MERGE (c:DocumentChunk {chunk_id: $chunk_id})
                   SET c.chunk_index = $index
                   MERGE (c)-[:PART_OF]->(d)
                   """)
        .bind(docId)
        .to("docId")
        .bind(chunkId)
        .to("chunk_id")
        .bind(index)
        .to("index")
        .run();
  }

  private boolean hasNextEdge(String fromChunk, String toChunk) {
    return neo4jClient
        .query(
            "MATCH (:DocumentChunk {chunk_id: $fromId})-[r:NEXT]->(:DocumentChunk {chunk_id: $toId}) RETURN count(r) > 0 AS exists")
        .bind(fromChunk)
        .to("fromId")
        .bind(toChunk)
        .to("toId")
        .fetchAs(Boolean.class)
        .one()
        .orElse(false);
  }

  private long countNode(String label) {
    return neo4jClient
        .query("MATCH (n:" + label + ") RETURN count(n)")
        .fetchAs(Long.class)
        .one()
        .orElse(0L);
  }

  private long countRelationships(String label) {
    return neo4jClient
        .query("MATCH ()-[r:" + label + "]->() RETURN count(r)")
        .fetchAs(Long.class)
        .one()
        .orElse(0L);
  }

  private Map<String, Object> getChunkProperties(String chunkId) {
    return neo4jClient
        .query("MATCH (c:DocumentChunk {chunk_id: $id}) RETURN properties(c) AS props")
        .bind(chunkId)
        .to("id")
        .fetch()
        .one()
        .orElseThrow(() -> new RuntimeException("Node not found"));
  }

  private Map<String, Object> getEntityProperties(String entityId) {
    return neo4jClient
        .query("MATCH (e:Entity {id: $id}) RETURN properties(e) AS props")
        .bind(entityId)
        .to("id")
        .fetch()
        .one()
        .orElseThrow(() -> new RuntimeException("Node not found"));
  }

  private Map<String, Object> getRelationshipProperties(
      String sourceId, String targetId, String type) {
    return neo4jClient
        .query(
            "MATCH (:Entity {id: $srcId})-[r:%s]->(:Entity {id: $tgtId}) RETURN properties(r) as props"
                .formatted(type))
        .bind(sourceId)
        .to("srcId")
        .bind(targetId)
        .to("tgtId")
        .fetch()
        .one()
        .orElseThrow(() -> new RuntimeException("Relationship not found"));
  }
}
