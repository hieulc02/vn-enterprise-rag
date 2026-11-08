package com.hieulc.insightragretrieval.service.context;

import com.hieulc.insightragretrieval.dto.context.FusionResult;

public interface ContextFusionService {
  FusionResult fuse(String query, int maxContextToken);
}
