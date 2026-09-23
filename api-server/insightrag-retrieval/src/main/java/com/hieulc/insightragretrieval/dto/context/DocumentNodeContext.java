package com.hieulc.insightragretrieval.dto.context;

import java.util.List;

public record DocumentNodeContext(
    DocumentNode documentNode, double wrrfScore, List<StructuralPath> structuralPaths) {
  public record StructuralPath(List<DocumentNode> nodes, List<NodeEdge> edges) {}
}
