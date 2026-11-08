package com.hieulc.insightragretrieval.service.tokenizer;

import com.google.genai.LocalTokenizer;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.TokenCountEstimator;

public class GeminiTokenEstimatorAdapter implements TokenCountEstimator {

  private final LocalTokenizer localTokenizer;

  public GeminiTokenEstimatorAdapter(String modelName) {
    this.localTokenizer = new LocalTokenizer(modelName);
  }

  @Override
  public int estimateTokenCountInText(String s) {
    if (s == null || s.isEmpty()) return 0;

    return localTokenizer.countTokens(s).totalTokens().orElse(0);
  }

  @Override
  public int estimateTokenCountInMessage(ChatMessage chatMessage) {
    if (chatMessage == null) return 0;

    int tokenCount = 0;
    switch (chatMessage) {
      case SystemMessage systemMessage ->
          tokenCount += estimateTokenCountInText(systemMessage.text());
      case UserMessage userMessage -> {
        if (userMessage.hasSingleText()) {
          tokenCount += estimateTokenCountInText(userMessage.singleText());
        } else {
          for (Content content : userMessage.contents()) {
            if (content instanceof TextContent textContent) {
              tokenCount += estimateTokenCountInText(textContent.text());
            }
          }
        }
      }
      case AiMessage aiMessage -> {
        tokenCount += estimateTokenCountInText(aiMessage.text());
      }
      case ToolExecutionResultMessage toolExecutionResultMessage ->
          tokenCount += estimateTokenCountInText(toolExecutionResultMessage.text());
      default -> {}
    }
    return tokenCount + 4; // structural boundary buffer
  }

  @Override
  public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
    if (messages == null) return 0;

    int tokenCount = 0;
    for (ChatMessage chatMessage : messages) {
      tokenCount += estimateTokenCountInMessage(chatMessage);
    }
    return tokenCount + 2; // structural boundary buffer
  }
}
