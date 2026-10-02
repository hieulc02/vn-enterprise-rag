package com.hieulc.insightragretrieval.dto.context;

import java.util.List;

public record DocumentNodeContext(
    DocumentNode documentNode,
    List<RelatedNodesContext> relatedEntities,
    List<DocumentChunkContext> sourceChunks) {
  public record RelatedNodesContext(
      DocumentNode source,
      NodeEdge edge,
      DocumentNode target) {}
}
