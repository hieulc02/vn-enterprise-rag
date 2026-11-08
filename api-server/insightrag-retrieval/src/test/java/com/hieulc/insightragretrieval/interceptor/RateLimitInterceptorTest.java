package com.hieulc.insightragretrieval.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.hieulc.insightragretrieval.service.security.RateLimitingService;
import io.github.bucket4j.ConsumptionProbe;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

  @Mock private RateLimitingService rateLimitingService;
  @Mock private ConsumptionProbe probe;
  private final String MOCK_USER_ID = "test-user-id";

  private RateLimitInterceptor rateLimitInterceptor;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    rateLimitInterceptor = new RateLimitInterceptor(rateLimitingService, new ObjectMapper());

    request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/chat");
    response = new MockHttpServletResponse();

    Jwt jwt = mock(Jwt.class);
    lenient().when(jwt.getSubject()).thenReturn(MOCK_USER_ID);
    Authentication authentication = mock(Authentication.class);
    lenient().when(authentication.getPrincipal()).thenReturn(jwt);

    SecurityContext securityContext = mock(SecurityContext.class);
    lenient().when(securityContext.getAuthentication()).thenReturn(authentication);

    SecurityContextHolder.setContext(securityContext);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void preHandle_returns_true_when_tokens_available() throws Exception {

    when(probe.isConsumed()).thenReturn(true);
    when(probe.getRemainingTokens()).thenReturn(9L);
    when(rateLimitingService.tryConsume(MOCK_USER_ID)).thenReturn(probe);

    boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

    assertThat(result).isTrue();
    assertThat(response.getHeader("RateLimit-Remaining")).isEqualTo("9");
  }

  @Test
  void preHandle_returns_false_and_set_status_to_429_when_tokens_exhausted() throws Exception {
    when(probe.isConsumed()).thenReturn(false);
    when(probe.getNanosToWaitForRefill()).thenReturn(TimeUnit.SECONDS.toNanos(5));
    when(rateLimitingService.tryConsume(MOCK_USER_ID)).thenReturn(probe);

    boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getHeader("Retry-After")).isEqualTo("5");

    String responseBody = response.getContentAsString();
    assertThat(responseBody).contains("Rate Limit Exceeded");
    assertThat(responseBody).contains("/api/v1/chat");
    assertThat(responseBody).contains("API quota has been exhausted");
  }

  @Test
  void preHandle_ignores_non_retryable_exception() throws Exception {
    SecurityContextHolder.clearContext();

    boolean result = rateLimitInterceptor.preHandle(request, response, new Object());
    assertThat(result).isTrue();
    verifyNoInteractions(rateLimitingService);
  }
}
