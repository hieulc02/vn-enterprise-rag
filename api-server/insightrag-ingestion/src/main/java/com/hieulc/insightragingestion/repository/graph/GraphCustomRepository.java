package com.hieulc.insightragingestion.repository.graph;

import com.hieulc.insightragingestion.dto.graph.DocumentChunk;
import com.hieulc.insightragingestion.dto.graph.Entity;
import com.hieulc.insightragingestion.dto.graph.GraphDeleteSummary;
import com.hieulc.insightragingestion.dto.graph.Relationship;
import java.util.List;

public interface GraphCustomRepository {
  void batchInsertChunks(String documentId, String documentKey, List<DocumentChunk> chunks);

  void batchInsertEntities(List<Entity> entities);

  void batchInsertRelationship(List<Relationship> relationships);

  int linkChunks(String documentId);

  GraphDeleteSummary deleteDocument(String documentId);
}
