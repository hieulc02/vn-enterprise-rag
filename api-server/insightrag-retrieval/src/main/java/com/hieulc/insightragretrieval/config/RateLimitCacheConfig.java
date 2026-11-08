package com.hieulc.insightragretrieval.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hieulc.insightragretrieval.config.properties.RateLimitProperties;
import io.github.bucket4j.Bucket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitCacheConfig {

  @Bean(name = "rateLimitBucketCache")
  public Cache<String, Bucket> rateLimitBucketCache(RateLimitProperties properties) {
    return Caffeine.newBuilder()
        .maximumSize(properties.cacheMaxSize())
        .expireAfterAccess(properties.cacheTtl())
        .recordStats()
        .build();
  }
}
