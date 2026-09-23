package com.hieulc.insightragretrieval.controller;

import com.hieulc.insightragretrieval.dto.chat.ChatRequestDto;
import com.hieulc.insightragretrieval.dto.chat.ChatResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Chat Operations", description = "Endpoints for interacting with the AI Chat")
public interface ChatApi {

  @Operation(
      summary = "Synchronous Chat",
      description =
          "Processes user query and returns the complete AI response in a single Json payload")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Success chat completion"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request validation",
            content = {@Content(schema = @Schema(implementation = ProblemDetail.class))}),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = {@Content(schema = @Schema(implementation = ProblemDetail.class))}),
      })
  ResponseEntity<ChatResponseDto> chatSync(ChatRequestDto chatRequest);

  @Operation(
      summary = "Streaming chat (SSE)",
      description = "Streams the AI response back to the client using Server-Sent Event")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Stream initiated successfully",
            content =
                @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(implementation = String.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request validation",
            content = {@Content(schema = @Schema(implementation = ProblemDetail.class))}),
      })
  SseEmitter chatStream(ChatRequestDto chatRequest);
}
