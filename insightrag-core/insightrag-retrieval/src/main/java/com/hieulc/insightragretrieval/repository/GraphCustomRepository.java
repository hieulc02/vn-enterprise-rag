package com.hieulc.insightragretrieval.repository;

import com.hieulc.insightragretrieval.dto.context.DocumentChunkContext;
import com.hieulc.insightragretrieval.dto.context.DocumentNodeContext;
import com.hieulc.insightragretrieval.dto.context.DocumentRelationshipContext;
import java.util.List;

public interface GraphCustomRepository {
  List<DocumentChunkContext> searchChunksHybrid(float[] queryVector, String keyword, int topK);

  List<DocumentNodeContext> searchNodeHybrid(float[] queryVector, String keyword, int topK);

  List<DocumentRelationshipContext> searchRelationshipSemantic(float[] queryVector, int topK);
}
