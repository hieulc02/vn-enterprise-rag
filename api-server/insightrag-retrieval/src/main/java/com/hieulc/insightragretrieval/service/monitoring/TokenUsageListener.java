package com.hieulc.insightragretrieval.service.monitoring;

import com.hieulc.insightragretrieval.event.TokenUsageEvent;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TokenUsageListener {

  @Async("taskExecutor")
  @EventListener
  public void handleTokenUsage(TokenUsageEvent event) {
    TokenUsage usage = event.tokenUsage();
    if (usage == null) {
      log.warn("Token usage not provided by the model for session:  {}", event.sessionId());
      return;
    }

    int inputTokens = usage.inputTokenCount();
    int outputTokens = usage.outputTokenCount();
    int totalTokens = usage.totalTokenCount();

    log.info(
        "Token Usage - User: {} | Session: {} | In: {} | Out: {} | Total: {}",
        event.userId(),
        event.sessionId(),
        inputTokens,
        outputTokens,
        totalTokens);
  }
}
