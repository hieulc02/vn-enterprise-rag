package com.hieulc.insightragretrieval.service.model;

import com.hieulc.insightragretrieval.dto.model.ModelCapacity;

public interface ModelRegistry {
  ModelCapacity getModelCapacity(String modelName);
}
