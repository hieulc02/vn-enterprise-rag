package com.hieulc.insightragretrieval.config.properties;

public class GenAiPropertiesFixtures {

  private GenAiPropertiesFixtures() {}

  public static GenAiProperties.GenAiPropertiesBuilder aGenAiProperties() {
    return GenAiProperties.builder()
        .baseUrl("https://generativelanguage.googleapis.com/v1beta")
        .modelMain("gemini-3-flash-preview")
        .modelLite("gemini-3.5-flash-lite")
        .modelTokenizer("gemini-3-pro-preview")
        .apiKey("mock-api-key")
        .queryLimitToken(100)
        .maxHistoryToken(200)
        .fallback(new GenAiProperties.FallbackProperties(32000, 8192))
        .memory(new GenAiProperties.MemoryProperties(5000, 60000));
  }

  public static GenAiProperties defaultGenAiProperties() {
    return aGenAiProperties().build();
  }
}
