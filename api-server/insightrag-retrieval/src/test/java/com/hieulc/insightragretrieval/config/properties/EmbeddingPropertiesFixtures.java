package com.hieulc.insightragretrieval.config.properties;

public class EmbeddingPropertiesFixtures {
  private EmbeddingPropertiesFixtures() {}

  public static EmbeddingProperties defaultEmbeddingProperties() {
    return new EmbeddingProperties("http:embedding-service:8081", "mock-model");
  }
}
