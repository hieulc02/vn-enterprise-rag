package com.hieulc.insightragretrieval.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hieulc.insightragretrieval.dto.context.DocumentChunkContext;
import com.hieulc.insightragretrieval.dto.context.DocumentNodeContext;
import com.hieulc.insightragretrieval.dto.context.DocumentRelationshipContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;

class GraphContextMapperTest {

  GraphContextMapper graphContextMapper;

  @BeforeEach
  void setUp() {
    graphContextMapper = new GraphContextMapper();
  }

  @Test
  void mapToChunkContext_maps_corresponding_to_DocumentChunkContext() {
    Record record = mock(Record.class);

    when(record.get("chunk_id")).thenReturn(Values.value("chunk-id-test"));
    when(record.get("text")).thenReturn(Values.value("chunk-text-test"));

    Map<String, Object> metadataMap = Map.of("document_id", "document-id-test", "page_number", 1);
    when(record.get("metadata")).thenReturn(Values.value(metadataMap));

    DocumentChunkContext result = graphContextMapper.mapToChunkContext(null, record);

    assertThat(result).isNotNull();
    assertThat(result.chunkId()).isEqualTo("chunk-id-test");
    assertThat(result.text()).isEqualTo("chunk-text-test");
    assertThat(result.metadata().documentId()).isEqualTo("document-id-test");
    assertThat(result.metadata().pageNumber()).isEqualTo(1);
  }

  @Test
  void mapToNodeContext_maps_corresponding_to_DocumentNodeContext_and_safely_handle_null_obs() {
    Record record = mock(Record.class);

    when(record.get("anchor_id")).thenReturn(Values.value("anchor-id"));
    when(record.get("anchor_title")).thenReturn(Values.value("anchor-title"));
    when(record.get("anchor_label")).thenReturn(Values.value(List.of("Entity", "Node")));
    when(record.get("anchor_props")).thenReturn(Values.value(Map.of("value", "prop")));

    Map<String, Object> chunkMap =
        Map.of(
            "chunk_id",
            "chunk-id-test",
            "metadata",
            Map.of("document_id", "document-id-test", "page_number", 1));
    when(record.get("source_chunks")).thenReturn(Values.value(List.of(chunkMap)));

    Map<String, Object> relatedEntityMap =
        Map.of(
            "direction", "OUTGOING",
            "rel_type", "COMPONENT_OF",
            "rel_desc", "component of",
            "target_id", "neighbor-id",
            "target_title", "neighbor-title",
            "target_labels", List.of("Entity"),
            "target_props", dummyProps("neighbor-prop"));

    when(record.get("related_entities")).thenReturn(Values.value(List.of(relatedEntityMap)));

    DocumentNodeContext result = graphContextMapper.mapToNodeContext(null, record);

    assertThat(result).isNotNull();
    assertThat(result.documentNode().id()).isEqualTo("anchor-id");
    assertThat(result.documentNode().title()).isEqualTo("anchor-title");
    assertThat(result.documentNode().labels()).containsExactly("Entity", "Node");

    assertThat(result.sourceChunks()).hasSize(1);
    DocumentChunkContext chunk = result.sourceChunks().getFirst();
    assertThat(chunk.chunkId()).isEqualTo("chunk-id-test");
    assertThat(chunk.text()).isEmpty();
    assertThat(chunk.metadata().documentId()).isEqualTo("document-id-test");
    assertThat(chunk.metadata().pageNumber()).isEqualTo(1);

    assertThat(result.relatedEntities()).hasSize(1);
    DocumentNodeContext.RelatedNodesContext entitiesContext = result.relatedEntities().getFirst();
    assertThat(entitiesContext.edge().edgeType()).isEqualTo("COMPONENT_OF");
    assertThat(entitiesContext.edge().edgeDesc()).isEqualTo("component of");
    assertThat(entitiesContext.source().id()).isEqualTo("anchor-id");
    assertThat(entitiesContext.target().id()).isEqualTo("neighbor-id");
    assertThat(entitiesContext.target().properties()).containsEntry("value", "neighbor-prop");
  }

  @Test
  void mapToRelationshipContext_maps_corresponding_to_DocumentRelationshipContext() {
    Record record = mock(Record.class);

    when(record.get("edge_type")).thenReturn(Values.value("RELATED_TO"));
    when(record.get("edge_desc")).thenReturn(Values.value("edge-desc"));
    when(record.get("semantic_score")).thenReturn(Values.value(0.80));

    Map<String, Object> sourceMap =
        Map.of("id", "source-id", "title", "source-title", "labels", List.of("Source-Label"));
    when(record.get("source_node")).thenReturn(Values.value(sourceMap));

    Map<String, Object> targetMap =
        Map.of("id", "target-id", "title", "target-title", "labels", List.of("Target-Label"));
    when(record.get("target_node")).thenReturn(Values.value(targetMap));

    DocumentRelationshipContext result = graphContextMapper.mapToRelationshipContext(null, record);

    assertThat(result).isNotNull();

    assertThat(result.nodeEdge().edgeType()).isEqualTo("RELATED_TO");
    assertThat(result.nodeEdge().edgeDesc()).isEqualTo("edge-desc");

    assertThat(result.sourceNode().id()).isEqualTo("source-id");
    assertThat(result.sourceNode().title()).isEqualTo("source-title");
    assertThat(result.sourceNode().labels()).containsExactly("Source-Label");

    assertThat(result.targetNode().id()).isEqualTo("target-id");
    assertThat(result.targetNode().title()).isEqualTo("target-title");
    assertThat(result.targetNode().labels()).containsExactly("Target-Label");
  }

  private Map<String, Object> dummyProps(String value) {
    Map<String, Object> props = new HashMap<>();
    props.put("value", value);
    props.put("embedding", null);
    props.put("id", null);
    props.put("title", null);

    return props;
  }
}
