package com.hieulc.insightragretrieval.service.model;

import com.hieulc.insightragretrieval.config.properties.GenAiProperties;
import com.hieulc.insightragretrieval.dto.model.ModelCapacity;
import com.hieulc.insightragretrieval.dto.model.ModelCapacityInfo;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class GeminiModelRegistry implements ModelRegistry {

  private final RestClient geminiRestClient;
  private final GenAiProperties genAiProperties;

  private final Map<String, ModelCapacity> modelCapacityCache = new ConcurrentHashMap<>();

  public GeminiModelRegistry(
      @Qualifier("geminiRestClient") RestClient geminiRestClient, GenAiProperties genAiProperties) {
    this.geminiRestClient = geminiRestClient;
    this.genAiProperties = genAiProperties;
  }

  @PostConstruct
  void initializeRegistry() {
    log.info("Fetching model capacity from Gemini API for model: {} ", genAiProperties.modelMain());

    try {
      ModelCapacityInfo response =
          geminiRestClient
              .get()
              .uri("/models/{model}", genAiProperties.modelMain())
              .retrieve()
              .body(ModelCapacityInfo.class);

      if (response != null && response.inputTokenLimit() > 0) {
        modelCapacityCache.put(
            genAiProperties.modelMain(),
            new ModelCapacity(
                genAiProperties.modelMain(),
                new ModelCapacityInfo(response.inputTokenLimit(), response.outputTokenLimit())));
        log.info(
            "Registered limit for {}. Input: {}, Output: {}",
            genAiProperties.modelMain(),
            response.inputTokenLimit(),
            response.outputTokenLimit());
      }

    } catch (Exception e) {
      log.warn(
          "Failed to fetch limits for model {}. Default to fallback. Reason: {}",
          genAiProperties.modelMain(),
          e.getMessage());
      modelCapacityCache.put(
          genAiProperties.modelMain(),
          new ModelCapacity(
              genAiProperties.modelMain(),
              new ModelCapacityInfo(
                  genAiProperties.fallback().inputLimit(),
                  genAiProperties.fallback().outputLimit())));
    }
  }

  @Override
  public ModelCapacity getModelCapacity(String modelName) {
    return modelCapacityCache.getOrDefault(
        modelName,
        new ModelCapacity(
            genAiProperties.modelMain(),
            new ModelCapacityInfo(
                genAiProperties.fallback().inputLimit(),
                genAiProperties.fallback().outputLimit())));
  }
}
