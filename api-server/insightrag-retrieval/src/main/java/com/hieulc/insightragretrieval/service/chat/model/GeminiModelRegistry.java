package com.hieulc.insightragretrieval.service.chat.model;

import com.hieulc.insightragretrieval.dto.ModelCapacity;
import com.hieulc.insightragretrieval.dto.ModelCapacityInfo;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class GeminiModelRegistry implements ModelRegistry {

  private final String primaryModel;
  private final RestClient geminiRestClient;
  private int fallbackInputLimit;
  private int fallbackOutputLimit;

  private final Map<String, ModelCapacity> modelCapacityCache = new ConcurrentHashMap<>();

  public GeminiModelRegistry(
      @Qualifier("geminiRestClient") RestClient geminiRestClient,
      @Value("${insightrag.ai.gemini.model-main}") String primaryModel,
      @Value("${insightrag.ai.gemini.fallback.input-limit}") int fallbackInputLimit,
      @Value("${insightrag.ai.gemini.fallback.output-limit}") int fallbackOutputLimit) {
    this.geminiRestClient = geminiRestClient;
    this.primaryModel = primaryModel;
    this.fallbackInputLimit = fallbackInputLimit;
    this.fallbackOutputLimit = fallbackOutputLimit;
  }

  @PostConstruct
  void initializeRegistry() {
    log.info("Fetching model capacity from Gemini API for model: {} ", primaryModel);

    try {
      ModelCapacityInfo response =
          geminiRestClient
              .get()
              .uri("/models/{model}", primaryModel)
              .retrieve()
              .body(ModelCapacityInfo.class);

      if (response != null && response.inputTokenLimit() > 0) {
        modelCapacityCache.put(
            primaryModel,
            new ModelCapacity(
                primaryModel,
                new ModelCapacityInfo(response.inputTokenLimit(), response.outputTokenLimit())));
        log.info(
            "Registered limit for {}. Input: {}, Output: {}",
            primaryModel,
            response.inputTokenLimit(),
            response.outputTokenLimit());
      }

    } catch (Exception e) {
      log.warn(
          "Failed to fetch limits for model {}. Default to fallback. Reason: {}",
          primaryModel,
          e.getMessage());
      modelCapacityCache.put(
          primaryModel,
          new ModelCapacity(
              primaryModel, new ModelCapacityInfo(fallbackInputLimit, fallbackOutputLimit)));
    }
  }

  @Override
  public ModelCapacity getModelCapacity(String modelName) {
    return modelCapacityCache.getOrDefault(
        modelName,
        new ModelCapacity(
            primaryModel, new ModelCapacityInfo(fallbackInputLimit, fallbackOutputLimit)));
  }
}
