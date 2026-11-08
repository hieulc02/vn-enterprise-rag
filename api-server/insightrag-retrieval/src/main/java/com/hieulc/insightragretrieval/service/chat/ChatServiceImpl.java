package com.hieulc.insightragretrieval.service.chat;

import com.hieulc.insightragretrieval.dto.chat.ChatRequestDto;
import com.hieulc.insightragretrieval.dto.chat.PreparedChatPayload;
import com.hieulc.insightragretrieval.event.TokenUsageEvent;
import com.hieulc.insightragretrieval.exception.infras.AiContentGenerateException;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {
  private final ChatPayloadBuilder payloadBuilder;
  private final ChatModel chatModel;
  private final StreamingChatModel streamingChatModel;

  private final ApplicationEventPublisher eventPublisher;

  @Override
  public String chat(ChatRequestDto request, String userId) {
    PreparedChatPayload payload = payloadBuilder.prepare(request, userId);

    ChatRequest chatRequest = ChatRequest.builder().messages(payload.messages()).build();
    ChatResponse response;
    try {
      response = chatModel.chat(chatRequest);
    } catch (Exception e) {
      log.error("LLM generation failed. User query not saved to prevent memory corruption", e);
      throw new AiContentGenerateException("AI processing failed. Please try again", e);
    }

    TokenUsage tokenUsage = response.tokenUsage();
    eventPublisher.publishEvent(new TokenUsageEvent(userId, request.sessionId(), tokenUsage));

    payload.chatMemory().add(UserMessage.from(payload.userQuery()));
    payload.chatMemory().add(response.aiMessage());

    return response.aiMessage().text();
  }

  @Override
  public void streamChat(ChatRequestDto request, String userId, ChatStreamCallback callback) {
    PreparedChatPayload payload = payloadBuilder.prepare(request, userId);

    ChatRequest chatRequest = ChatRequest.builder().messages(payload.messages()).build();
    streamingChatModel.chat(
        chatRequest,
        new StreamingChatResponseHandler() {
          @Override
          public void onPartialResponse(String partialResponse) {
            callback.onResponse(partialResponse);
          }

          @Override
          public void onCompleteResponse(ChatResponse chatResponse) {
            try {

              TokenUsage tokenUsage = chatResponse.tokenUsage();
              eventPublisher.publishEvent(
                  new TokenUsageEvent(userId, request.sessionId(), tokenUsage));

              payload.chatMemory().add(UserMessage.from(payload.userQuery()));
              payload.chatMemory().add(chatResponse.aiMessage());
              callback.onComplete();
            } catch (Exception e) {
              callback.onError(e);
            }
          }

          @Override
          public void onError(Throwable throwable) {
            callback.onError(throwable);
          }
        });
  }
}
