package com.hieulc.insightragretrieval.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hieulc.insightragretrieval.config.SecurityConfig;
import com.hieulc.insightragretrieval.exception.GlobalExceptionHandler;
import com.hieulc.insightragretrieval.exception.infras.AiContentGenerateException;
import com.hieulc.insightragretrieval.interceptor.RateLimitInterceptor;
import com.hieulc.insightragretrieval.security.JwtAuthenticationEntryPoint;
import com.hieulc.insightragretrieval.service.chat.ChatService;
import com.hieulc.insightragretrieval.service.chat.ChatStreamCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(ChatController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class ChatControllerTest {

  private final String USER_ID = "user-id-test";
  private final String REQUEST_PAYLOAD = "{\"sessionId\":\"session-id\", \"userQuery\":\"query\"}";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ChatService chatService;
  @MockitoBean private RateLimitInterceptor rateLimitInterceptor;
  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() throws Exception {
    when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
  }

  @Test
  void chatSync_returns_ok_and_response_json() throws Exception {
    when(chatService.chat(any(), anyString())).thenReturn("AI response.");

    mockMvc
        .perform(
            post("/api/v1/chat")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(REQUEST_PAYLOAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("session-id"))
        .andExpect(jsonPath("$.response").value("AI response."));
  }

  @Test
  void chatSync_returns_badRequest_when_sessionId_is_missing() throws Exception {
    String invalidPayload = "{\"userQuery\":\"valid query\"}";
    mockMvc
        .perform(
            post("/api/v1/chat")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request payload failed validation"))
        .andExpect(jsonPath("$.sessionId").value("Session ID is required"));
  }

  @Test
  void chatSync_returns_badRequest_when_userQuery_is_blank() throws Exception {
    String invalidPayload = "{\"sessionId\":\"session-id\", \"userQuery\":\"   \"}";
    mockMvc
        .perform(
            post("/api/v1/chat")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request payload failed validation"))
        .andExpect(jsonPath("$.userQuery").value("User query cannot be empty"));
  }

  @Test
  void chatSync_returns_badRequest_when_userQuery_exceeds_max_length() throws Exception {
    String longQuery = "a".repeat(2001);
    String invalidPayload =
        String.format("{\"sessionId\":\"session-id\", \"userQuery\":\"%s\"}", longQuery);
    mockMvc
        .perform(
            post("/api/v1/chat")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request payload failed validation"))
        .andExpect(jsonPath("$.userQuery").value("User query cannot exceed 2000 characters"));
  }

  @Test
  void chatSync_should_return_401_problemDetail_when_no_token_provided() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"session-id\", \"userQuery\":\"query\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"insightrag\""))
        .andExpect(jsonPath("$.title").value("Authentication Failed"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.instance").value("/api/v1/chat"))
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void chatStream_should_emit_SseEvent_and_complete() throws Exception {

    doAnswer(
            invocation -> {
              ChatStreamCallback callback = invocation.getArgument(2);
              callback.onResponse("Stream ");
              callback.onResponse("response. ");
              callback.onComplete();
              return null;
            })
        .when(chatService)
        .streamChat(any(), anyString(), any());

    MvcResult mvcResult =
        mockMvc
            .perform(
                post("/api/v1/chat/stream")
                    .with(jwt().jwt(jwt -> jwt.subject("session-id")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(REQUEST_PAYLOAD))
            .andExpect(status().isOk())
            .andReturn();

    String sseResponse = mvcResult.getResponse().getContentAsString();
    assertThat(sseResponse).contains("data:Stream \n\n");
    assertThat(sseResponse).contains("data:response. \n\n");
  }

  @Test
  void chatStream_should_emit_errorEvent_onFailure() throws Exception {
    AiContentGenerateException simulatedError = new AiContentGenerateException("API down", null);

    doAnswer(
            invocation -> {
              ChatStreamCallback callback = invocation.getArgument(2);
              callback.onResponse("Stream ");
              callback.onError(simulatedError);
              return null;
            })
        .when(chatService)
        .streamChat(any(), anyString(), any());

    MvcResult mvcResult =
        mockMvc
            .perform(
                post("/api/v1/chat/stream")
                    .with(jwt().jwt(jwt -> jwt.subject(USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(REQUEST_PAYLOAD))
            .andExpect(status().isOk())
            .andReturn();

    String sseResponse = mvcResult.getResponse().getContentAsString();
    assertThat(sseResponse).contains("data:Stream \n\n");
    assertThat(sseResponse).contains("event:error\n");
    assertThat(sseResponse).contains("API down");
  }

  @Test
  void chatStream_should_return_401_problemDetail_when_invalid_token_provided() throws Exception {
    when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Invalid token signature"));

    mockMvc
        .perform(
            post("/api/v1/chat/stream")
                .header("Authorization", "Bearer invalid.jwt.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_PAYLOAD))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"insightrag\""))
        .andExpect(jsonPath("$.title").value("Authentication Failed"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.instance").value("/api/v1/chat/stream"));
  }
}
