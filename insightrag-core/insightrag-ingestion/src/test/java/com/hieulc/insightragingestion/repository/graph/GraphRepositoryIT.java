package com.hieulc.insightragingestion.repository.graph;

import static com.hieulc.insightragingestion.factory.GraphTestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.hieulc.insightragingestion.base.AbstractNeo4jIT;
import com.hieulc.insightragingestion.dto.graph.DocumentChunk;
import com.hieulc.insightragingestion.dto.graph.Entity;
import com.hieulc.insightragingestion.dto.graph.GraphDeleteSummary;
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

  private final String docKey = "test-document";
  private final String docId = "document-id";

  @AfterEach
  void cleanDb() {
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();
  }

  @Test
  void batchInsertChunks_creates_document_graph_in_single_batch() {
    var chunks = generateListChunks(docKey, 2);

    repository.batchInsertChunks(docId, docKey, chunks);

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
  void batchInsertChunks_should_be_idempotent() {
    var chunk = createDefaultChunk("c1", docKey);

    repository.batchInsertChunks(docId, docKey, List.of(chunk));
    repository.batchInsertChunks(docId, docKey, List.of(chunk));

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
  void batchInsertChunks_updates_properties_when_chunk_exists() {
    var initChunk = createChunk("c1", docId, "INIT-TEXT", 1);
    repository.batchInsertChunks(docId, docKey, List.of(initChunk));

    var updatedChunk = createChunk("c1", docId, "UPDATED-TEXT", 2);
    repository.batchInsertChunks(docId, docKey, List.of(updatedChunk));

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) getChunkProperties("c1").get("props");

    assertThat(props).containsEntry("text", "UPDATED-TEXT").containsEntry("chunk_index", 2L);
  }

  @Test
  void batchInsertChunks_flattens_chunk_metadata_properties() {
    var chunk = createChunk("c2", docId, "FLATTEN-TEXT", 22);
    repository.batchInsertChunks(docId, docKey, List.of(chunk));

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) getChunkProperties("c2").get("props");

    assertThat(props).containsEntry("document_key", docId).containsEntry("page_number", 22L);
  }

  @Test
  void batchInsertChunks_handles_null_properties_safely() {
    var chunk = new DocumentChunk("c3", "METADATA-EMPTY", null, 0, new float[] {});
    repository.batchInsertChunks(docId, docKey, List.of(chunk));

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) getChunkProperties("c3").get("props");
    assertThat(props).containsEntry("text", "METADATA-EMPTY");
    assertThat(props.containsKey("page_number")).isFalse();
  }

  @Test
  void batchInsertEntities_inserts_and_flatten_entities_properties() {
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
  void batchInsertEntities_should_be_idempotent() {
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

    long entityCount = countNode("Node");
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
  void batchInsertEntities_should_successfully_insert_orphan_entity() {
    var entity = createEntity("e3", null, null);
    repository.batchInsertEntities(List.of(entity));

    long entityCount = countNode("Node");
    assertThat(entityCount).isEqualTo(1L);

    long edgeCount = countEdge("EXTRACTED_FROM", "Node", "DocumentChunk");
    assertThat(edgeCount).isEqualTo(0L);
  }

  @Test
  void batchInsertEntities_should_not_override_entity_properties_with_null_value() {
    var baseAliases = List.of("Base Alias");
    var entity = createEntity("e4", baseAliases, null);

    repository.batchInsertEntities(List.of(entity));
    var updatedEntity = createEntity("e4", null, null);
    repository.batchInsertEntities(List.of(updatedEntity));

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) getEntityProperties("e4").get("props");

    assertThat(props).containsEntry("aliases", baseAliases);
  }

  @Test
  void batchInsertEntities_should_link_entity_with_chunk() {
    createDummyDocumentChunk("chunk-1");
    createDummyDocumentChunk("chunk-2");

    var entity = createEntity("e5", null, Set.of("chunk-1", "chunk-2"));
    repository.batchInsertEntities(List.of(entity));

    long edgeCount = countEdge("EXTRACTED_FROM", "Node", "DocumentChunk");

    assertThat(edgeCount).isEqualTo(2L);
  }

  @Test
  void batchInsertEntities_should_not_link_entity_with_chunk_when_sourceChunkIds_is_null() {

    var entityWithEmptyChunks = createEntity("e6", null, Set.of());
    var entityWithNullChunks = createEntity("e7", null, null);

    repository.batchInsertEntities(List.of(entityWithEmptyChunks, entityWithNullChunks));

    long edgeCount = countEdge("EXTRACTED_FROM", "Node", "DocumentChunk");
    assertThat(edgeCount).isEqualTo(0L);
  }

  @Test
  void batchInsertRelationship_should_insert_and_group_relationships_by_type() {

    createDummyEntity("e1");
    createDummyEntity("e2");
    createDummyEntity("e3");

    var rel1 = createRelationship("e1", "e2", "RELATED_TO", null, 1.0);
    var rel2 = createRelationship("e2", "e3", "COMPONENT_OF", null, 1.0);

    repository.batchInsertRelationship(List.of(rel1, rel2));

    long relatedCount = countEdge("RELATED_TO", "Node", "Node");
    long componentCount = countEdge("COMPONENT_OF", "Node", "Node");

    assertThat(relatedCount).isEqualTo(1L);
    assertThat(componentCount).isEqualTo(1L);
  }

  @Test
  void batchInsertRelationship_flattens_relationship_properties() {

    createDummyEntity("e1");
    createDummyEntity("e2");

    var relationship =
        createRelationship("e1", "e2", "RELATED_TO", Set.of("chunk-1", "chunk-2"), 1.0);

    repository.batchInsertRelationship(List.of(relationship));

    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) getRelationshipProperties("e1", "e2", "RELATED_TO").get("props");

    assertThat(props)
        .containsEntry("description", "Default-Description")
        .containsEntry("weight", 1.0)
        .containsEntry("props", "value");

    @SuppressWarnings("unchecked")
    List<String> chunkIds = (List<String>) props.get("source_chunk_ids");

    assertThat(chunkIds).containsExactlyInAnyOrder("chunk-1", "chunk-2");
  }

  @Test
  void batchInsertRelationship_should_be_idempotent_and_update_not_null_properties() {
    createDummyEntity("e1");
    createDummyEntity("e2");

    var initRelationship = createRelationship("e1", "e2", "RELATED_TO", Set.of("chunk-1"), 1.0);
    repository.batchInsertRelationship(List.of(initRelationship));
    var updateRelationship = createRelationship("e1", "e2", "RELATED_TO", null, 0.9);
    repository.batchInsertRelationship(List.of(updateRelationship));

    assertThat(countEdge("RELATED_TO", "Node", "Node")).isEqualTo(1L);

    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) getRelationshipProperties("e1", "e2", "RELATED_TO").get("props");
    assertThat(props)
        .containsEntry("weight", 0.9)
        .containsEntry("source_chunk_ids", List.of("chunk-1"));
  }

  @Test
  void batchInsertRelationship_ignores_inserting_relationship_if_source_or_target_is_missing() {
    createDummyEntity("e1");

    var orphanRelationship = createRelationship("e1", "missing-target", "RELATED_TO", null, 1.0);

    repository.batchInsertRelationship(List.of(orphanRelationship));

    long orphanCount = countEdge("RELATED_TO", "Node", "Node");
    assertThat(orphanCount).isEqualTo(0L);
  }

  @Test
  void linkChunks_link_with_document_in_ascendingOrder() {
    seedChunk(docId, "c1", 1);
    seedChunk(docId, "c3", 3);
    seedChunk(docId, "c2", 2);
    seedChunk(docId, "c4", 4);

    int chunkCount = repository.linkChunks(docId);

    assertThat(chunkCount).isEqualTo(4);
    assertThat(countEdge("PART_OF", "DocumentChunk", "Document")).isEqualTo(chunkCount);

    assertThat(hasNextEdge("c1", "c2")).isEqualTo(true);
    assertThat(hasNextEdge("c2", "c3")).isEqualTo(true);
    assertThat(hasNextEdge("c3", "c4")).isEqualTo(true);
    assertThat(hasNextEdge("c4", "c1")).isEqualTo(false);

    long edgeCount = countEdge("NEXT", "DocumentChunk", "DocumentChunk");
    assertThat(edgeCount).isEqualTo(3L);
  }

  @Test
  void linkChunks_should_be_idempotent_when_merging_duplicate_documents() {
    seedChunk(docId, "c1", 1);
    seedChunk(docId, "c2", 2);

    repository.linkChunks(docId);
    repository.linkChunks(docId);

    long documentChunkCount = countEdge("PART_OF", "DocumentChunk", "Document");
    assertThat(documentChunkCount).isEqualTo(2L);
    long edgeCount = countEdge("NEXT", "DocumentChunk", "DocumentChunk");
    assertThat(edgeCount).isEqualTo(1L);
  }

  @Test
  void linkChunks_should_not_link_nonExisting_document() {
    repository.linkChunks("non-existing-document");

    long documentChunkCount = countEdge("PART_OF", "DocumentChunk", "Document");
    assertThat(documentChunkCount).isEqualTo(0);

    long edgeCount = countEdge("NEXT", "DocumentChunk", "DocumentChunk");
    assertThat(edgeCount).isEqualTo(0);
  }

  @Test
  void linkChunks_stills_link_orphan_chunk() {
    seedChunk(docId, "c1", 1);

    int singleChunkCount = repository.linkChunks(docId);

    assertThat(singleChunkCount).isEqualTo(1);
    assertThat(hasNextEdge("c1", "c1")).isFalse();
    assertThat(countEdge("PART_OF", "DocumentChunk", "Document")).isEqualTo(1);
    assertThat(countEdge("NEXT", "DocumentChunk", "DocumentChunk")).isEqualTo(0L);
  }

  @Test
  void deleteDocument_deletes_all_shared_nodes_and_edges() {
    neo4jClient
        .query(
            """
            MERGE (d1:Document {document_id: 'doc_1'})
            MERGE (c1:DocumentChunk {chunk_id: 'chunk_1'})-[:PART_OF]->(d1)

            MERGE (d2:Document {document_id: 'doc_2'})
            MERGE (c2:DocumentChunk {chunk_id: 'chunk_2'})-[:PART_OF]->(d2)

            MERGE (e1:Node {id: 'node-1'})
            MERGE (e2:Node {id: 'node-2'})

            MERGE (e1)-[:EXTRACTED_FROM]->(c1)
            MERGE (e1)-[:EXTRACTED_FROM]->(c2)

            MERGE (e1)-[r:RELATED_TO]->(e2)
            SET r.source_chunk_ids = ['chunk_1', 'chunk_2']
            """)
        .run();

    GraphDeleteSummary summary = repository.deleteDocument("doc_1");
    assertThat(summary.nodesDeleted()).isEqualTo(2);
    assertThat(summary.relationshipsDeleted()).isEqualTo(2);

    long doc1Count =
        neo4jClient
            .query("MATCH (d:Document {document_id: 'doc_1'}) RETURN count(d)")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
    assertThat(doc1Count).isZero();

    long survivingEntities =
        neo4jClient
            .query("MATCH (e:Node {id: 'node-1'}) RETURN count(e)")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
    assertThat(survivingEntities).isEqualTo(1);

    String[] survivingCitations =
        neo4jClient
            .query("MATCH ()-[r:RELATED_TO]->() RETURN r.source_chunk_ids")
            .fetchAs(String[].class)
            .one()
            .orElse(new String[0]);
    assertThat(survivingCitations).contains("chunk_2");
  }

  @Test
  void deleteDocument_returns_empty_when_document_does_not_exist() {
    GraphDeleteSummary summary = repository.deleteDocument("non-exist-doc");

    assertThat(summary.wasAlreadyDeleted()).isTrue();
    assertThat(summary.nodesDeleted()).isZero();
    assertThat(summary.relationshipsDeleted()).isZero();
  }

  private void createDummyEntity(String id) {
    neo4jClient.query("CREATE (:Node {id: $id})").bind(id).to("id").run();
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

  private long countEdge(String edgeType, String from, String to) {
    String query = String.format("MATCH (:%s)-[r:%s]->(:%s) RETURN count(r)", from, edgeType, to);

    return neo4jClient.query(query).fetchAs(Long.class).one().orElse(0L);
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
        .query("MATCH (e:Node {id: $id}) RETURN properties(e) AS props")
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
            "MATCH (:Node {id: $srcId})-[r:%s]->(:Node {id: $tgtId}) RETURN properties(r) as props"
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
