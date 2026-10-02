package com.hieulc.insightragretrieval.factory;

import com.hieulc.insightragretrieval.dto.context.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ContextDataTestFactory {

  public static DocumentNodeContext createDocumentNodeContext() {
    return new DocumentNodeContext(
        dummyDocumentNode("anchor", Map.of("value", "props-anchor"), "Anchor"),
        List.of(
            new DocumentNodeContext.RelatedNodesContext(
                dummyDocumentNode("anchor", Map.of("value", "props-anchor"), "Anchor"),
                dummyRelationship("COMPONENT_OF", ""),
                dummyDocumentNode("target", Map.of("value", "props-target"), "Subject")),
            new DocumentNodeContext.RelatedNodesContext(
                dummyDocumentNode("anchor", Map.of("value", "props-anchor"), "Anchor"),
                dummyRelationship("HAS_OBSERVATION", ""),
                dummyDocumentNode(null, Map.of("value", 123.0), "Observation"))),
        List.of(createDocumentChunkContext(""), createDocumentChunkContext("")));
  }

  public static DocumentChunkContext createDocumentChunkContext(String text) {
    return new DocumentChunkContext(
        "chunk-id", text, new DocumentChunkContext.ChunkMetadata("doc-test", 1));
  }

  public static DocumentRelationshipContext createRelationshipContext(
      String source, String target) {
    return new DocumentRelationshipContext(
        dummyRelationship("RELATED_TO", "edge-desc"),
        0.80,
        dummyDocumentNode(source, Map.of(), "Subject"),
        dummyDocumentNode(target, Map.of(), "Subject"));
  }

  public static NodeEdge dummyRelationship(String edgeType, String edgeDesc) {
    return new NodeEdge(edgeType, edgeDesc);
  }

  public static DocumentNode dummyDocumentNode(
      Object title, Map<String, Object> props, String label) {

    String id = "default-id-" + label;
    if (title instanceof String titleStr) {
      id = UUID.nameUUIDFromBytes(titleStr.getBytes(StandardCharsets.UTF_8)).toString();
    }

    return new DocumentNode(id, title, List.of("Node", "Entity", label), props);
  }
}
