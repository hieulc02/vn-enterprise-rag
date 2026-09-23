package com.hieulc.insightragretrieval.service.chat;

import com.hieulc.insightragretrieval.dto.TokenBudget;
import com.hieulc.insightragretrieval.dto.chat.ChatRequestDto;
import com.hieulc.insightragretrieval.dto.chat.PreparedChatPayload;
import com.hieulc.insightragretrieval.dto.context.FusionResult;
import com.hieulc.insightragretrieval.service.chat.model.ModelRegistry;
import com.hieulc.insightragretrieval.service.chat.policy.HistoryEvictionPolicy;
import com.hieulc.insightragretrieval.service.context.ContextFusionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatPayloadBuilder {

  private final ChatMemoryStore chatMemoryStore;
  private final ModelRegistry modelRegistry;

  private final ContextFusionService contextFusionService;
  private final TokenCountEstimator tokenCountEstimator;
  private final HistoryEvictionPolicy historyEvictionPolicy;

  @Value("${insightrag.ai.gemini.model-main}")
  private String modelName;

  @Value("${insightrag.ai.gemini.max-history-token}")
  private int maxHistoryToken;

  @Value("${insightrag.ai.gemini.query-limit-token}")
  private int userQueryLimitToken;

  private final SystemMessage systemMessage =
      SystemMessage.from(
          """
        You are InsightRAG, a forensic financial analyst and enterprise AI auditor.
        Your primary directive is to answer the user's query with absolute precision, strictly using ONLY the data provided within the <context> XML tags.

        ### STRICT OPERATIONAL CONSTRAINTS:
        1. ZERO HALLUCINATION: Answer strictly using ONLY the <context> XML tags. If the data is absent, state that you do not have enough information.
        2. NUMERICAL IMMUTABILITY: Never round numbers, alter decimal precision, or simplify currency scales.
        3. TEMPORAL ACCURACY: Preserve the distinction between fiscal/calendar years and actuals/forecasts.
        4. MANDATORY CITATION: Every factual claim must end with its exact source citation in brackets (e.g., [Source: SEC-10K, Page: 12]).
        5. LANGUAGE PROTOCOL:
           - Default language is Vietnamese. Always respond in Vietnamese unless explicitly prompted otherwise.
           - If the user writes their query in English or another language, mirror the user's query language.
           - Regardless of the language used, do NOT translate proper nouns, ticker symbols, financial acronyms, or metadata tags in citations.
       """);

  public PreparedChatPayload prepare(ChatRequestDto chatRequest) {
    String query = chatRequest.userQuery();

    ChatMemory memory = loadMemory(chatRequest.sessionId());
    List<ChatMessage> history = sanitizeHistory(memory.messages());

    TokenBudget budget =
        TokenBudget.of(
            modelRegistry.getModelCapacity(modelName).capacityInfo().inputTokenLimit(),
            tokenCountEstimator.estimateTokenCountInMessage(systemMessage),
            tokenCountEstimator.estimateTokenCountInMessages(history),
            tokenCountEstimator.estimateTokenCountInText(query),
            userQueryLimitToken);

    FusionResult fusionResult = contextFusionService.fuse(query, budget.availableForContext());
    UserMessage enrichedPrompt = buildEnrichedPrompt(query, fusionResult.context());

    int projectedTokens =
        tokenCountEstimator.estimateTokenCountInMessage(enrichedPrompt)
            + budget.systemTokens()
            + budget.historyTokens();
    history =
        historyEvictionPolicy.evictToFitLimit(history, projectedTokens, budget.maxModelLimit());

    List<ChatMessage> finalMessages = new ArrayList<>();
    finalMessages.add(systemMessage);
    finalMessages.addAll(history);
    finalMessages.add(enrichedPrompt);

    return new PreparedChatPayload(finalMessages, memory, query, chatRequest.sessionId());
  }

  private ChatMemory loadMemory(String sessionId) {
    return TokenWindowChatMemory.builder()
        .id(sessionId)
        .chatMemoryStore(chatMemoryStore)
        .maxTokens(maxHistoryToken, tokenCountEstimator)
        .build();
  }

  private List<ChatMessage> sanitizeHistory(List<ChatMessage> history) {
    List<ChatMessage> sanitized = new ArrayList<>(history);
    while (!history.isEmpty() && history.getFirst() instanceof AiMessage) {
      history.removeFirst();
    }
    return sanitized;
  }

  private UserMessage buildEnrichedPrompt(String query, String graphContext) {
    String prompt =
        String.format(
            "Here is the retrieved knowledge base data:\n"
                + "<context>\n%s\n</context>\n\n"
                + "Based ONLY on the <context> above, answer this question:\n"
                + "<question>\n%s\n</question>",
            graphContext, query);
    return UserMessage.from(prompt);
  }
}
