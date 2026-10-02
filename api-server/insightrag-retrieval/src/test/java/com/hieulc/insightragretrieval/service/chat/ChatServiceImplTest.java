package com.hieulc.insightragretrieval.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hieulc.insightragretrieval.dto.chat.ChatRequestDto;
import com.hieulc.insightragretrieval.dto.chat.PreparedChatPayload;
import com.hieulc.insightragretrieval.exception.infras.AiContentGenerateException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {
  @Mock private ChatPayloadBuilder chatPayloadBuilder;

  @Mock private StreamingChatModel streamingChatModel;
  @Mock private ChatModel chatModel;
  @Mock private ChatStreamCallback callback;
  @Mock private ChatMemory chatMemoryMock;
  @Captor private ArgumentCaptor<StreamingChatResponseHandler> handlerCaptor;
  @InjectMocks private ChatServiceImpl chatService;

  @Test
  void chat_returns_text_response_and_save_memory() {
    var request = createChatRequest();
    var dummyPayload = createChatPayload();

    when(chatPayloadBuilder.prepare(request)).thenReturn(dummyPayload);

    AiMessage aiResponse = AiMessage.from("Sync response.");
    ChatResponse chatResponse = ChatResponse.builder().aiMessage(aiResponse).build();
    when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);

    String response = chatService.chat(request);

    assertThat(response).isEqualTo("Sync response.");
    verify(chatMemoryMock).add(any(UserMessage.class));
    verify(chatMemoryMock).add(aiResponse);
  }

  @Test
  void chat_throws_ai_exception_and_not_save_memory() {
    var request = createChatRequest();
    var dummyPayload = createChatPayload();

    when(chatPayloadBuilder.prepare(request)).thenReturn(dummyPayload);
    when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("LLM failed"));

    assertThatThrownBy(() -> chatService.chat(request))
        .isInstanceOf(AiContentGenerateException.class)
        .hasMessageContaining("AI processing failed. Please try again");

    verify(chatMemoryMock, never()).add(any(UserMessage.class));
    verify(chatMemoryMock, never()).add(any(AiMessage.class));
  }

  @Test
  void streamChat_relays_partial_response_callback_and_save_memory_on_complete() {
    var request = createChatRequest();
    var dummyPayload = createChatPayload();

    when(chatPayloadBuilder.prepare(request)).thenReturn(dummyPayload);

    chatService.streamChat(request, callback);

    verify(streamingChatModel).chat(any(ChatRequest.class), handlerCaptor.capture());
    StreamingChatResponseHandler responseHandler = handlerCaptor.getValue();

    responseHandler.onPartialResponse("Stream ");
    responseHandler.onPartialResponse("response. ");
    AiMessage finalAiResponse = AiMessage.from("Stream response.");
    responseHandler.onCompleteResponse(ChatResponse.builder().aiMessage(finalAiResponse).build());

    verify(callback).onResponse("Stream ");
    verify(callback).onResponse("response. ");
    verify(callback).onComplete();

    verify(chatMemoryMock).add(any(UserMessage.class));
    verify(chatMemoryMock).add(finalAiResponse);
  }

  @Test
  void streamChat_routes_on_error_and_not_save_memory() {
    var request = createChatRequest();
    var dummyPayload = createChatPayload();

    when(chatPayloadBuilder.prepare(request)).thenReturn(dummyPayload);

    chatService.streamChat(request, callback);

    verify(streamingChatModel).chat(any(ChatRequest.class), handlerCaptor.capture());
    StreamingChatResponseHandler responseHandler = handlerCaptor.getValue();

    RuntimeException aiError = new RuntimeException("LLM Timeout");
    responseHandler.onError(aiError);

    verify(callback).onError(aiError);
    verify(chatMemoryMock, never()).add(any(UserMessage.class));
    verify(chatMemoryMock, never()).add(any(AiMessage.class));
  }

  private PreparedChatPayload createChatPayload() {
    return new PreparedChatPayload(
        List.of(UserMessage.from("query")), chatMemoryMock, "query", "test-session-id");
  }

  private ChatRequestDto createChatRequest() {
    return new ChatRequestDto("test-session-id", "query");
  }
}
