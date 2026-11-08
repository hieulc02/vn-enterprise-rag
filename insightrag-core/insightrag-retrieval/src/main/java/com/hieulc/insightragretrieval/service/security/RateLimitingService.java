package com.hieulc.insightragretrieval.service.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.hieulc.insightragretrieval.config.properties.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RateLimitingService {

  private final Cache<String, Bucket> userBuckets;
  private final RateLimitProperties properties;

  public RateLimitingService(
      @Qualifier("rateLimitBucketCache") Cache<String, Bucket> userBuckets,
      RateLimitProperties properties) {
    this.userBuckets = userBuckets;
    this.properties = properties;
  }

  public ConsumptionProbe tryConsume(String userId) {
    Bucket bucket = userBuckets.get(userId, this::createNewBucket);
    return bucket.tryConsumeAndReturnRemaining(1);
  }

  private Bucket createNewBucket(String userId) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(properties.capacity())
            .refillGreedy(properties.refillTokens(), properties.refillDuration())
            .build();
    return Bucket.builder().addLimit(limit).build();
  }
}
