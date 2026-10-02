package com.hieulc.insightragretrieval.service.chat;

import com.hieulc.insightragretrieval.config.properties.GenAiProperties;
import com.hieulc.insightragretrieval.dto.chat.ChatRequestDto;
import com.hieulc.insightragretrieval.dto.chat.PreparedChatPayload;
import com.hieulc.insightragretrieval.dto.context.FusionResult;
import com.hieulc.insightragretrieval.dto.tokenizer.TokenBudget;
import com.hieulc.insightragretrieval.service.chat.policy.HistoryEvictionPolicy;
import com.hieulc.insightragretrieval.service.context.ContextFusionService;
import com.hieulc.insightragretrieval.service.model.ModelRegistry;
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
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatPayloadBuilder {

  private final ChatMemoryStore chatMemoryStore;
  private final ModelRegistry modelRegistry;

  private final ContextFusionService contextFusionService;
  private final TokenCountEstimator tokenCountEstimator;
  private final HistoryEvictionPolicy historyEvictionPolicy;
  private final GenAiProperties genAiProperties;

  private final SystemMessage systemMessage =
      SystemMessage.from(
          """
        You are InsightRAG, a forensic financial analyst and enterprise AI auditor.
        Your primary directive is to answer the user's query with absolute precision, strictly synthesizing the provided data from <source_chunks>, <entities>, and <semantic_connections>.
        Note that some of these XML tags may be empty depending on the query; rely only on the data present.

        ### DATA SYNTHESIS PROTOCOL:
        1. TEXT GROUNDING (<source_chunks>): Treat raw text chunks as primary evidence for verbatim rules, terms, and numerical data.
        2. ANCHOR ENTITIES (<entities>): Use entity definitions and their <relationships> to understand entity attributes, roles, and directly connected neighborhood context.
        3. GLOBAL CONNECTION (<semantic_connections>): Use these standalone relational paths to link entities across disparate documents when direct chunk text is absent.

        ### STRICT OPERATIONAL CONSTRAINTS:
        1. ZERO HALLUCINATION: Answer strictly using ONLY the provided XML tags, explicitly state you lack the information. Do not infer outside knowledge.
        2. NUMERICAL IMMUTABILITY: Never round numbers, alter decimal precision, or simplify currency scales.
        3. TEMPORAL ACCURACY: Preserve the distinction between fiscal/calendar years and actuals/forecasts.
        4. MANDATORY CITATION: Every factual claim must end with its exact source citation in brackets (e.g., [Source: SEC-10K, Page: 12]).
        5. LANGUAGE PROTOCOL: Mirror the user's query language (default to Vietnamese). Do NOT translate proper nouns, ticker symbols, financial acronyms, or metadata tags in citations.
       """);

  public PreparedChatPayload prepare(ChatRequestDto chatRequest) {
    String query = chatRequest.userQuery();

    ChatMemory memory = loadMemory(chatRequest.sessionId());
    List<ChatMessage> history = sanitizeHistory(memory.messages());

    TokenBudget budget =
        TokenBudget.of(
            modelRegistry
                .getModelCapacity(genAiProperties.modelMain())
                .capacityInfo()
                .inputTokenLimit(),
            tokenCountEstimator.estimateTokenCountInMessage(systemMessage),
            tokenCountEstimator.estimateTokenCountInMessages(history),
            tokenCountEstimator.estimateTokenCountInText(query),
            genAiProperties.queryLimitToken());

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
    finalMessages.add(enrichedPrompt);
    finalMessages.addAll(history);

    return new PreparedChatPayload(finalMessages, memory, query, chatRequest.sessionId());
  }

  private ChatMemory loadMemory(String sessionId) {
    return TokenWindowChatMemory.builder()
        .id(sessionId)
        .chatMemoryStore(chatMemoryStore)
        .maxTokens(genAiProperties.maxHistoryToken(), tokenCountEstimator)
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
