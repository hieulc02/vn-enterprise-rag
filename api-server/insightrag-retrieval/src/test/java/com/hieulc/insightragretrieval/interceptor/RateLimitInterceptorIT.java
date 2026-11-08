package com.hieulc.insightragretrieval.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.benmanes.caffeine.cache.Cache;
import com.hieulc.insightragretrieval.config.RateLimitCacheConfig;
import com.hieulc.insightragretrieval.config.SecurityConfig;
import com.hieulc.insightragretrieval.config.WebMvcConfig;
import com.hieulc.insightragretrieval.config.properties.RateLimitProperties;
import com.hieulc.insightragretrieval.controller.ChatController;
import com.hieulc.insightragretrieval.exception.GlobalExceptionHandler;
import com.hieulc.insightragretrieval.security.JwtAuthenticationEntryPoint;
import com.hieulc.insightragretrieval.service.chat.ChatService;
import com.hieulc.insightragretrieval.service.security.RateLimitingService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChatController.class)
@Import({
  WebMvcConfig.class,
  RateLimitInterceptor.class,
  RateLimitingService.class,
  RateLimitCacheConfig.class,
  JwtAuthenticationEntryPoint.class,
  GlobalExceptionHandler.class,
  SecurityConfig.class // required for JWT authentication to work
})
@ActiveProfiles("test")
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitInterceptorIT {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private ChatService chatService;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Autowired
  @Qualifier("rateLimitBucketCache")
  private Cache<String, Bucket> rateLimitBucketCache;

  private final String USER_ID = "user-id-test";
  private final String REQUEST_PAYLOAD = "{\"sessionId\":\"session-id\", \"userQuery\":\"query\"}";
  private final int CAPACITY = 3;

  @BeforeEach
  void setUp() {
    rateLimitBucketCache.invalidateAll();
    when(chatService.chat(any(), anyString())).thenReturn("Mocked response");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void chatSync_should_enforce_rate_limit() throws Exception {
    for (int i = 0; i < CAPACITY; i++) {
      mockMvc
          .perform(
              post("/api/v1/chat")
                  .with(jwt().jwt(jwt -> jwt.subject(USER_ID)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(REQUEST_PAYLOAD))
          .andExpect(status().isOk())
          .andExpect(header().exists("RateLimit-Remaining"));
    }

    mockMvc
        .perform(
            post("/api/v1/chat")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_PAYLOAD))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.title").value("Rate Limit Exceeded"))
        .andExpect(jsonPath("$.instance").value("/api/v1/chat"));
  }

  @Test
  void chatStream_should_enforce_rate_limit() throws Exception {
    doNothing().when(chatService).streamChat(any(), anyString(), any());

    for (int i = 0; i < CAPACITY; i++) {
      mockMvc
          .perform(
              post("/api/v1/chat/stream")
                  .with(jwt().jwt(jwt -> jwt.subject(USER_ID)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(REQUEST_PAYLOAD))
          .andExpect(status().isOk())
          .andExpect(request().asyncStarted())
          .andExpect(header().exists("RateLimit-Remaining"));
    }

    mockMvc
        .perform(
            post("/api/v1/chat/stream")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_PAYLOAD))
        .andExpect(status().isTooManyRequests())
        .andExpect(request().asyncNotStarted())
        .andExpect(header().exists("Retry-After"))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.title").value("Rate Limit Exceeded"))
        .andExpect(jsonPath("$.instance").value("/api/v1/chat/stream"));
  }

  @Test
  void ignore_unmapped_endpoints() throws Exception {
    mockMvc
        .perform(post("/api/v1/public/status").contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().doesNotExist("RateLimit-Remaining"));
  }
}
