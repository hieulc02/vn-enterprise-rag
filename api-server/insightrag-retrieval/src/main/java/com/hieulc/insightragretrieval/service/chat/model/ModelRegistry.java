package com.hieulc.insightragretrieval.service.chat.model;

import com.hieulc.insightragretrieval.dto.ModelCapacity;

public interface ModelRegistry {
  ModelCapacity getModelCapacity(String modelName);
}
