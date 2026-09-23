package com.hieulc.insightragretrieval.dto.context;

import java.util.List;
import java.util.Map;

public record DocumentRelationshipContext(
    NodeEdge nodeEdge,
    double sematicScore,
    DocumentNodeContext sourceNode,
    DocumentNodeContext targetNode) {
  public record DocumentNodeContext(
      DocumentNode nodeMetadata, List<Map<String, Object>> obsPropsList) {}
}
