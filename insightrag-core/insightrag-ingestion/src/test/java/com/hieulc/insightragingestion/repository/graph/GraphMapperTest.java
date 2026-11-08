package com.hieulc.insightragingestion.repository.graph;

import static com.hieulc.insightragingestion.factory.GraphTestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.hieulc.insightragingestion.dto.graph.Relationship;
import java.util.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GraphMapperTest {

  @Nested
  class ToChunkMapTest {

    @Test
    void should_map_complete_chunk() {
      var chunk = createChunk("chunk-id", "doc-key", "chunk-text", 1);

      Map<String, Object> result = GraphMapper.toChunkMap(chunk);

      assertThat(result)
          .containsEntry("chunk_id", "chunk-id")
          .containsEntry("text", "chunk-text")
          .containsEntry("chunk_index", 1)
          .containsKey("embedding");

      float[] actualEmbedding = (float[]) result.get("embedding");
      assertThat(actualEmbedding).containsExactly(0.1f, 0.2f);

      @SuppressWarnings("unchecked")
      Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");

      assertThat(metadata).containsEntry("document_key", "doc-key").containsEntry("page_number", 1);
    }

    @Test
    void should_map_chunk_with_empty_metadata() {
      var chunk = createChunk("chunk-id", null, "chunk-text", 0);

      Map<String, Object> result = GraphMapper.toChunkMap(chunk);

      assertThat(result)
          .containsEntry("chunk_id", "chunk-id")
          .containsEntry("text", "chunk-text")
          .containsKey("metadata");

      //noinspection unchecked
      assertThat((Map<String, Object>) result.get("metadata")).isEmpty();
    }
  }

  @Nested
  class ToEntityNodeMapTest {

    @Test
    void should_map_complete_entity_and_sanitize_properties() {
      var entity = createEntity("entity-id", List.of("Entity"), Set.of());

      Map<String, Object> result = GraphMapper.toEntityNodeMap(entity);

      assertThat(result)
          .containsEntry("id", "entity-id")
          .containsEntry("title", "Default-Title")
          .containsEntry("description", "Entity-Description")
          .containsEntry("labels", List.of("Node", "Aliases"))
          .containsEntry("aliases", List.of("Entity"));

      @SuppressWarnings("unchecked")
      Map<String, Object> properties = (Map<String, Object>) result.get("properties");

      assertThat(properties)
          .containsEntry("attribute", "fake")
          .doesNotContainKeys("null-prop", "empty-list");

      float[] actualEmbedding = (float[]) properties.get("embedding");
      assertThat(actualEmbedding).containsExactly(0.1f, 0.2f);
    }
  }

  @Nested
  class ToEntityEdgeMapTest {
    @Test
    void should_map_entity_edge() {
      var entity = createEntity("entity-id", List.of("Entity"), Set.of("chunk_1", "chunk_2"));

      Map<String, Object> result = GraphMapper.toEntityEdgeMap(entity);

      assertThat(result)
          .containsEntry("id", "entity-id")
          .containsEntry("chunk_ids", Set.of("chunk_1", "chunk_2"));
    }
  }

  @Nested
  class ToRelationshipMapTest {

    @Test
    void should_map_complete_relationship_and_sanitize_props() {
      var relationship =
          createRelationship("source-node", "target-node", "RELATED_TO", Set.of("chunk_1"), 1.0);

      Map<String, Object> result = GraphMapper.toRelationshipMap(relationship);

      assertThat(result)
          .containsEntry("source", "source-node")
          .containsEntry("target", "target-node");

      @SuppressWarnings("unchecked")
      Map<String, Object> properties = (Map<String, Object>) result.get("props");

      assertThat(properties)
          .containsEntry("props", "value")
          .containsEntry("description", "Default-Description")
          .containsEntry("source_chunk_ids", Set.of("chunk_1"))
          .containsEntry("weight", 1.0)
          .doesNotContainKeys("null-prop", "empty-list");

      float[] actualEmbedding = (float[]) properties.get("embedding");
      assertThat(actualEmbedding).containsExactly(0.1f, 0.2f);
    }

    @Test
    void should_map_relationship_with_missing_optional() {
      var relationship =
          new Relationship(
              "test-rel-empty",
              "source-node",
              "target-node",
              "RELATED_TO",
              null,
              1.0,
              Set.of(),
              null);

      Map<String, Object> result = GraphMapper.toRelationshipMap(relationship);

      assertThat(result)
          .containsEntry("source", "source-node")
          .containsEntry("target", "target-node");

      @SuppressWarnings("unchecked")
      Map<String, Object> properties = (Map<String, Object>) result.get("props");

      assertThat(properties)
          .doesNotContainKeys("source_chunk_ids", "description")
          .doesNotContainKeys("null-prop", "empty-list");
    }
  }

  @Nested
  class SanitizePropertiesTest {
    @Test
    void should_return_empty_map_for_null_or_empty_input() {
      GraphMapper.sanitizeProperties(null).isEmpty();
      GraphMapper.sanitizeProperties(Collections.emptyMap()).isEmpty();
    }

    @Test
    void should_remove_null_value() {
      Map<String, Object> properties = new HashMap<>();
      properties.put("key", "value");
      properties.put("props", null);

      Map<String, Object> result = GraphMapper.sanitizeProperties(properties);

      assertThat(result).containsEntry("key", "value").doesNotContainKey("props");
    }

    @Test
    void should_remove_empty_collection() {
      Map<String, Object> properties = new HashMap<>();
      properties.put("key", "value");
      properties.put("embedding", List.of());
      properties.put("source_chunk_ids", Set.of());

      Map<String, Object> result = GraphMapper.sanitizeProperties(properties);

      assertThat(result)
          .containsEntry("key", "value")
          .doesNotContainKeys("embedding", "source_chunk_ids");
    }

    @Test
    void should_convert_embedding_list_to_float_array() {
      Map<String, Object> properties = Map.of("embedding", List.of(0.1f, 0.2f, 0.3));

      Map<String, Object> result = GraphMapper.sanitizeProperties(properties);

      assertThat(result.get("embedding"))
          .isInstanceOf(float[].class)
          .isEqualTo(new float[] {0.1f, 0.2f, 0.3f});
    }

    @Test
    void should_return_valid_primitive_and_object_types() {
      Map<String, Object> properties =
          Map.of(
              "string", "value", "integer", 42, "boolean", true, "valid-list", List.of("a", "b"));

      Map<String, Object> result = GraphMapper.sanitizeProperties(properties);

      assertThat(result)
          .containsEntry("string", "value")
          .containsEntry("integer", 42)
          .containsEntry("boolean", true)
          .containsEntry("valid-list", List.of("a", "b"));
    }
  }
}
