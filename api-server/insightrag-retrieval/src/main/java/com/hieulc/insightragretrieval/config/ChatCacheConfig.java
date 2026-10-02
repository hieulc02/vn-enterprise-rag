package com.hieulc.insightragretrieval.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hieulc.insightragretrieval.config.properties.GenAiProperties;
import dev.langchain4j.data.message.ChatMessage;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ChatCacheConfig {

  private final GenAiProperties genAiProperties;

  @Bean
  public Cache<Object, List<ChatMessage>> cache() {
    return Caffeine.newBuilder()
        .maximumSize(genAiProperties.memory().maxSession())
        .expireAfterAccess(Duration.ofMinutes(genAiProperties.memory().ttlMinutes()))
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
