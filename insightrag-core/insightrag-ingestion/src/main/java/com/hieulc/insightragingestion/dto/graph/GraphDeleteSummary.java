package com.hieulc.insightragingestion.dto.graph;

public record GraphDeleteSummary(int nodesDeleted, int relationshipsDeleted) {
  public boolean wasAlreadyDeleted() {
    return nodesDeleted == 0 && relationshipsDeleted == 0;
  }
}
