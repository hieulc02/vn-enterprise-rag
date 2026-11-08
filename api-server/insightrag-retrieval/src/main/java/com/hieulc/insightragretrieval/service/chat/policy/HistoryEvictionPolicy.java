package com.hieulc.insightragretrieval.service.chat.policy;

import com.hieulc.insightragretrieval.exception.infras.TokenExceededLimitException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HistoryEvictionPolicy {

  private final TokenCountEstimator tokenCountEstimator;

  public List<ChatMessage> evictToFitLimit(
      List<ChatMessage> currentHistory, int currentTotalToken, int tokenLimit) {

    if (currentTotalToken <= tokenLimit) {
      return currentHistory;
    }

    List<ChatMessage> degradedHistory = new ArrayList<>(currentHistory);
    int tokens = currentTotalToken;

    while (tokens > tokenLimit && degradedHistory.size() >= 2) {
      ChatMessage message = degradedHistory.getFirst();
      ChatMessage removedUser = degradedHistory.removeFirst();
      tokens -= tokenCountEstimator.estimateTokenCountInMessage(removedUser);
      if (message instanceof UserMessage) {
        if (!degradedHistory.isEmpty() && degradedHistory.getFirst() instanceof AiMessage) {
          ChatMessage removeAi = degradedHistory.removeFirst();
          tokens -= tokenCountEstimator.estimateTokenCountInMessage(removeAi);
        }
      }
    }
    if (tokens > tokenLimit) {
      throw new TokenExceededLimitException(
          String.format(
              "Request payload exceeds model limit (%d) even after history degradation.",
              tokenLimit));
    }
    return degradedHistory;
  }
}
