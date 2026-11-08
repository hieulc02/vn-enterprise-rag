package com.hieulc.insightragretrieval.interceptor;

import com.hieulc.insightragretrieval.service.security.RateLimitingService;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
  private final RateLimitingService rateLimitingService;
  private final ObjectMapper objectMapper;

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler)
      throws Exception {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      String userId = jwt.getSubject();

      ConsumptionProbe probe = rateLimitingService.tryConsume(userId);

      if (probe.isConsumed()) {
        response.addHeader("RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        return true;
      } else {
        long waitForRefill = probe.getNanosToWaitForRefill() / TimeUnit.SECONDS.toNanos(1);
        response.addHeader("Retry-After", String.valueOf(waitForRefill));
        sendRateLimitExceededResponse(request, response, waitForRefill);
        return false;
      }
    }
    return true;
  }

  private void sendRateLimitExceededResponse(
      HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
      throws Exception {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "API quota has been exhausted. Please try again in " + retryAfterSeconds + " seconds.");
    problemDetail.setTitle("Rate Limit Exceeded");
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    objectMapper.writeValue(response.getOutputStream(), problemDetail);
  }
}
