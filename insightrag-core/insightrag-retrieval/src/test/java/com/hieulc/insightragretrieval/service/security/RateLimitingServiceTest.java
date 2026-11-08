package com.hieulc.insightragretrieval.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hieulc.insightragretrieval.config.properties.RateLimitProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimitingServiceTest {

  private RateLimitingService rateLimitingService;
  private final String MOCK_USER_ID = "user-id-test";
  private final int CAPACITY = 3;

  @BeforeEach
  void setUp() {
    RateLimitProperties properties =
        new RateLimitProperties(
            CAPACITY, CAPACITY, Duration.ofMinutes(1), 100, Duration.ofMinutes(5));

    Cache<String, Bucket> cache = Caffeine.newBuilder().build();
    rateLimitingService = new RateLimitingService(cache, properties);
  }

  @Test
  void tryConsume_rejects_when_capacity_is_full() {
    for (int i = 0; i < CAPACITY; i++) {
      ConsumptionProbe probe = rateLimitingService.tryConsume(MOCK_USER_ID);
      assertThat(probe.isConsumed()).isTrue();
      assertThat(CAPACITY - (i + 1)).isEqualTo(probe.getRemainingTokens());
    }
    ConsumptionProbe probe = rateLimitingService.tryConsume(MOCK_USER_ID);
    assertThat(probe.isConsumed()).isFalse();
    assertThat(probe.getNanosToWaitForRefill()).isGreaterThan(0);
  }

  @Test
  void tryConsume_tracks_different_threads_independently() {
    // exhaust thread 1
    for (int i = 0; i < CAPACITY; i++) {
      rateLimitingService.tryConsume(MOCK_USER_ID);
    }
    assertThat(rateLimitingService.tryConsume(MOCK_USER_ID).isConsumed()).isFalse();

    // thread 2 still have full capacity
    ConsumptionProbe probe = rateLimitingService.tryConsume("user-id-with-full-capacity");
    assertThat(probe.isConsumed()).isTrue();
    assertThat(probe.getRemainingTokens()).isEqualTo(CAPACITY - 1);
  }
}
