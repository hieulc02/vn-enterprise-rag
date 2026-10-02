package com.hieulc.insightragretrieval.service.chat.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hieulc.insightragretrieval.exception.infras.TokenExceededLimitException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoryEvictionPolicyTest {

  @Mock private TokenCountEstimator tokenCountEstimator;

  @InjectMocks HistoryEvictionPolicy historyEvictionPolicy;

  @Test
  void evictToFitLimit_evicts_oldest_pair_history_messages_when_token_exceeded_limit() {
    List<ChatMessage> currentHistory = generateHistoryMessage(4);

    when(tokenCountEstimator.estimateTokenCountInMessage(any(ChatMessage.class))).thenReturn(10);

    List<ChatMessage> degradedHistory =
        historyEvictionPolicy.evictToFitLimit(currentHistory, 120, 100);

    assertThat(degradedHistory).hasSize(2);
    assertThat(degradedHistory.getFirst()).isInstanceOf(UserMessage.class);
    if (degradedHistory.getFirst() instanceof UserMessage userMessage) {
      assertThat(userMessage.singleText()).isEqualTo("user-message-3");
    }
    assertThat(degradedHistory.getLast()).isInstanceOf(AiMessage.class);
    if (degradedHistory.getFirst() instanceof AiMessage aiMessage) {
      assertThat(aiMessage.text()).isEqualTo("ai-message-4");
    }
  }

  @Test
  void evictToFitLimit_throws_exception_when_token_limit_exceed_after_eviction() {
    List<ChatMessage> currentHistory = generateHistoryMessage(2);

    when(tokenCountEstimator.estimateTokenCountInMessage(any(ChatMessage.class))).thenReturn(10);

    assertThatThrownBy(() -> historyEvictionPolicy.evictToFitLimit(currentHistory, 150, 100))
        .isInstanceOf(TokenExceededLimitException.class)
        .hasMessageContaining(
            "Request payload exceeds model limit (100) even after history degradation.");
  }

  private List<ChatMessage> generateHistoryMessage(int numberOfMessages) {
    List<ChatMessage> currentHistory = new ArrayList<>();
    for (int i = 1; i <= numberOfMessages; ++i) {
      if (i % 2 != 0) {
        currentHistory.add(createUserMessage("user-message-" + i));
      } else {
        currentHistory.add(createAiMessage("ai-message-" + i));
      }
    }
    return currentHistory;
  }

  private UserMessage createUserMessage(String message) {
    return UserMessage.from(message);
  }

  private AiMessage createAiMessage(String message) {
    return AiMessage.from(message);
  }
}
