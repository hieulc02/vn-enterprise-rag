package com.hieulc.insightragingestion.repository;

import com.hieulc.insightragingestion.dto.DocumentChunk;
import com.hieulc.insightragingestion.dto.Entity;
import com.hieulc.insightragingestion.dto.Relationship;
import java.util.List;

public interface GraphCustomRepository {
  void batchInsertChunks(String documentId, List<DocumentChunk> chunks);

  void batchInsertEntities(List<Entity> entities);

  void batchInsertRelationship(List<Relationship> relationships);

  int linkChunks(String documentId);
}
