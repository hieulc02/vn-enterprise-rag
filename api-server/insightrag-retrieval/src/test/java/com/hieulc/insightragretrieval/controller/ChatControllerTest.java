package com.hieulc.insightragretrieval.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hieulc.insightragretrieval.exception.GlobalExceptionHandler;
import com.hieulc.insightragretrieval.exception.infras.AiContentGenerateException;
import com.hieulc.insightragretrieval.service.chat.ChatService;
import com.hieulc.insightragretrieval.service.chat.ChatStreamCallback;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(ChatController.class)
@Import(GlobalExceptionHandler.class)
class ChatControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ChatService chatService;

  @MockitoBean private GlobalExceptionHandler globalExceptionHandler;

  @Test
  void chatSync_returns_ok_and_response_json() throws Exception {
    when(chatService.chat(any())).thenReturn("AI response.");

    mockMvc
        .perform(
            post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"sessionId\":\"session-id\", \"userQuery\":\"query\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("session-id"))
        .andExpect(jsonPath("$.response").value("AI response."));
  }

  @Test
  void chatSync_returns_badRequest_when_sessionId_is_missing() throws Exception {
    String invalidPayload = "{\"userQuery\":\"valid query\"}";
    mockMvc
        .perform(
            post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(invalidPayload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request payload failed validation"))
        .andExpect(jsonPath("$.sessionId").value("Session ID is required"));
  }

  @Test
  void chatSync_returns_badRequest_when_userQuery_is_blank() throws Exception {
    String invalidPayload = "{\"sessionId\":\"session-id\", \"userQuery\":\"   \"}";
    mockMvc
        .perform(
            post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(invalidPayload))
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
            post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(invalidPayload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request payload failed validation"))
        .andExpect(jsonPath("$.userQuery").value("User query cannot exceed 2000 characters"));
  }

  @Test
  void chatStream_should_emit_SseEvent_and_complete() throws Exception {

    doAnswer(
            invocation -> {
              ChatStreamCallback callback = invocation.getArgument(1);
              callback.onResponse("Stream ");
              callback.onResponse("response. ");
              callback.onComplete();
              return null;
            })
        .when(chatService)
        .streamChat(any(), any());

    MvcResult mvcResult =
        mockMvc
            .perform(
                post("/api/v1/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sessionId\":\"session-id\", \"userQuery\":\"query\"}"))
            .andExpect(status().isOk())
            .andReturn();

    String sseResponse = mvcResult.getResponse().getContentAsString();
    assertThat(sseResponse).contains("data:Stream \n\n");
    assertThat(sseResponse).contains("data:response. \n\n");
  }

  @Test
  void chatStream_should_emit_errorEvent_onFailure() throws Exception {
    AiContentGenerateException simulatedError = new AiContentGenerateException("API down", null);
    ProblemDetail fakeDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "API down");
    when(globalExceptionHandler.resolveToProblemDetail(simulatedError)).thenReturn(fakeDetail);

    doAnswer(
            invocation -> {
              ChatStreamCallback callback = invocation.getArgument(1);
              callback.onResponse("Stream ");
              callback.onError(simulatedError);
              return null;
            })
        .when(chatService)
        .streamChat(any(), any());

    MvcResult mvcResult =
        mockMvc
            .perform(
                post("/api/v1/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sessionId\":\"session-id\", \"userQuery\":\"query\"}"))
            .andExpect(status().isOk())
            .andReturn();

    String sseResponse = mvcResult.getResponse().getContentAsString();
    assertThat(sseResponse).contains("data:Stream \n\n");
    assertThat(sseResponse).contains("event:error\n");
    assertThat(sseResponse).contains("API down");
  }
}
