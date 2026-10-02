package com.hieulc.insightragretrieval.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hieulc.insightragretrieval.config.properties.GenAiProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@RequiredArgsConstructor
@Slf4j
public class SpringCacheConfig {

  private final GenAiProperties genAiProperties;

  @Bean
  CacheManager cacheManager() {
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager("queryPreparationCache", "queryAnalysisCache");
    cacheManager.setCaffeine(
        Caffeine.newBuilder()
            .maximumSize(genAiProperties.memory().maxSession())
            .expireAfterAccess(Duration.ofMinutes(genAiProperties.memory().ttlMinutes()))
            .recordStats()
            .removalListener(
                (key, value, cause) -> {
                  if (cause.wasEvicted()) {
                    log.info(
                        "Query cache evicted from heap. Session Id: {}, Reason: {}",
                        key != null ? key.hashCode() : "null",
                        cause);
                  }
                }));
    return cacheManager;
  }
}
