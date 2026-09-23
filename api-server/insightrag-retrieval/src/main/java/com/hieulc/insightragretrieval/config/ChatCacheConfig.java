package com.hieulc.insightragretrieval.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.message.ChatMessage;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ChatCacheConfig {

  @Value("${insightrag.ai.gemini.memory.max-session}")
  private long maxSessions;

  @Value("${insightrag.ai.gemini.memory.ttl-minutes}")
  private long ttlMinutes;

  @Bean
  public Cache<Object, List<ChatMessage>> cache() {
    return Caffeine.newBuilder()
        .maximumSize(maxSessions)
        .expireAfterAccess(Duration.ofMinutes(ttlMinutes))
        .recordStats()
        .removalListener(
            (key, value, cause) -> {
              if (cause.wasEvicted()) {
                log.info("Chat session evicted from heap. Session Id: {}, Reason: {}", key, cause);
              }
            })
        .build();
  }

}
