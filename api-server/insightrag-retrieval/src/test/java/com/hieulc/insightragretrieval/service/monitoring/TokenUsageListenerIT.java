package com.hieulc.insightragretrieval.service.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hieulc.insightragretrieval.config.AsyncConfig;
import com.hieulc.insightragretrieval.event.TokenUsageEvent;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = {TokenUsageListener.class, AsyncConfig.class})
class TokenUsageListenerIT {

  private final String USER_ID = "user-id-test";
  private final String SESSION_ID = "session-id-test";

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Test
  void handleTokenUsage_logs_token_usage_asynchronously(CapturedOutput output) {
    TokenUsageEvent event = new TokenUsageEvent(USER_ID, SESSION_ID, new TokenUsage(100, 50, 150));

    eventPublisher.publishEvent(event);

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              assertThat(output.getOut())
                  .contains(
                      "Token Usage - User: user-id-test | Session: session-id-test  | In: 100 | Out: 50 | Total: 150");
            });
  }
}
