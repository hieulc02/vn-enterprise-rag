package com.hieulc.insightragretrieval.config.properties;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("insightrag.context")
public record ContextFusionProperties(
    int retrievalTimeoutSeconds, String activeStrategy, Map<String, StrategyLimits> strategies) {
  public record StrategyLimits(int chunkLimit, int nodeLimit, int relationshipLimit) {}

  public StrategyLimits getActiveLimits() {
    return strategies.getOrDefault(activeStrategy, new StrategyLimits(3, 3, 3));
  }
}
