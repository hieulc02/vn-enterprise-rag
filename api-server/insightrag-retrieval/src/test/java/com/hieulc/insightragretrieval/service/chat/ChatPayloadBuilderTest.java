package com.hieulc.insightragretrieval.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.hieulc.insightragretrieval.dto.ModelCapacity;
import com.hieulc.insightragretrieval.dto.ModelCapacityInfo;
import com.hieulc.insightragretrieval.dto.chat.ChatRequestDto;
import com.hieulc.insightragretrieval.dto.chat.PreparedChatPayload;
import com.hieulc.insightragretrieval.dto.context.FusionResult;
import com.hieulc.insightragretrieval.exception.infras.TokenExceededLimitException;
import com.hieulc.insightragretrieval.service.chat.model.ModelRegistry;
import com.hieulc.insightragretrieval.service.chat.policy.HistoryEvictionPolicy;
import com.hieulc.insightragretrieval.service.context.ContextFusionService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatPayloadBuilderTest {

  @Mock private ModelRegistry modelRegistry;

  @Mock private ContextFusionService contextFusionService;
  @Mock private TokenCountEstimator tokenCountEstimator;
  @Mock private HistoryEvictionPolicy historyEvictionPolicy;

  @InjectMocks private ChatPayloadBuilder chatPayloadBuilder;

  private final String modelName = "gemini-3-pro-preview";
  private final SystemMessage systemMessage = SystemMessage.from("You are a Mock test AI.");

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(chatPayloadBuilder, "modelName", modelName);
    ReflectionTestUtils.setField(chatPayloadBuilder, "maxHistoryToken", 200);
    ReflectionTestUtils.setField(chatPayloadBuilder, "userQueryLimitToken", 100);
    ReflectionTestUtils.setField(chatPayloadBuilder, "systemMessage", systemMessage);
  }

  @Test
  void prepare_returns_payload_containing_all_messages_within_token_limit() {
    ChatRequestDto request = createChatRequest();
    when(modelRegistry.getModelCapacity(modelName))
        .thenReturn(new ModelCapacity(modelName, new ModelCapacityInfo(200, 100)));
    when(tokenCountEstimator.estimateTokenCountInMessage(any(ChatMessage.class))).thenReturn(20);
    when(tokenCountEstimator.estimateTokenCountInMessages(anyList())).thenReturn(40);
    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(10);

    String graphContext = genDummyGraphContext();
    when(contextFusionService.fuse(anyString(), anyInt()))
        .thenReturn(new FusionResult(graphContext, 70));

    PreparedChatPayload payload = chatPayloadBuilder.prepare(request);

    assertThat(payload).isNotNull();
    assertThat(payload.messages()).hasSize(2);
    assertThat(payload.messages().getFirst()).isInstanceOf(SystemMessage.class);
    if (payload.messages().getLast() instanceof SystemMessage sysMessage) {
      assertThat(sysMessage.text()).isEqualTo(systemMessage.text());
    }
    assertThat(payload.messages().getLast()).isInstanceOf(UserMessage.class);
    if (payload.messages().getLast() instanceof UserMessage userMessage) {
      assertThat(userMessage.singleText()).contains(graphContext);
    }
  }

  @Test
  void prepare_throws_exception_when_user_query_exceeded_token_limit() {
    ChatRequestDto request = createChatRequest();

    when(modelRegistry.getModelCapacity(modelName))
        .thenReturn(new ModelCapacity(modelName, new ModelCapacityInfo(200, 100)));
    when(tokenCountEstimator.estimateTokenCountInMessage(any(ChatMessage.class))).thenReturn(20);
    when(tokenCountEstimator.estimateTokenCountInMessages(anyList())).thenReturn(40);
    when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(101);

    assertThatThrownBy(() -> chatPayloadBuilder.prepare(request))
        .isInstanceOf(TokenExceededLimitException.class)
        .hasMessage("Query token count (101) exceeds max limit of 100");
  }

  private ChatRequestDto createChatRequest() {
    return new ChatRequestDto("session-id-test", "query");
  }

  private String genDummyGraphContext() {
    return """
    <chunks></chunks>\n
    <graph_relationships></graph_relationships>\n
    <graph_entities></graph_entities>\n
    """;
  }
}
