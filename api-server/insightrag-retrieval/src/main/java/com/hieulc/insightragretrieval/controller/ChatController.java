package com.hieulc.insightragretrieval.controller;

import com.hieulc.insightragretrieval.dto.chat.ChatRequestDto;
import com.hieulc.insightragretrieval.dto.chat.ChatResponseDto;
import com.hieulc.insightragretrieval.exception.GlobalExceptionHandler;
import com.hieulc.insightragretrieval.service.chat.ChatService;
import com.hieulc.insightragretrieval.service.chat.ChatStreamCallback;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController implements ChatApi {
  private final ChatService chatService;
  private final GlobalExceptionHandler globalExceptionHandler;

  @Override
  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ChatResponseDto> chatSync(@Valid @RequestBody ChatRequestDto chatRequest) {
    String responseText = chatService.chat(chatRequest);
    return ResponseEntity.ok(new ChatResponseDto(chatRequest.sessionId(), responseText));
  }

  @Override
  @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter chatStream(@Valid @RequestBody ChatRequestDto chatRequest) {
    SseEmitter sseEmitter = new SseEmitter(120_000L);

    sseEmitter.onTimeout(sseEmitter::complete);
    sseEmitter.onError(
        e ->
            log.error(
                "SSE Stream terminated with error for session: {}", chatRequest.sessionId(), e));
    chatService.streamChat(
        chatRequest,
        new ChatStreamCallback() {
          @Override
          public void onResponse(String partialResponse) {
            try {
              sseEmitter.send(SseEmitter.event().data(partialResponse));
            } catch (IOException e) {
              sseEmitter.complete();
              throw new RuntimeException(e);
            }
          }

          @Override
          public void onComplete() {
            sseEmitter.complete();
          }

          @Override
          public void onError(Throwable throwable) {
            ProblemDetail problemDetail = globalExceptionHandler.resolveToProblemDetail(throwable);
            try {
              sseEmitter.send(SseEmitter.event().name("error").data(problemDetail));
            } catch (IOException e) {
              log.error("Server failed to send event to client", e);
            } finally {
              sseEmitter.complete();
            }
          }
        });
    return sseEmitter;
  }
}
