package com.hieulc.insightragretrieval.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for initiating a chat request")
public record ChatRequestDto(
    @Schema(description = "Unique identifier for chat session context", example = "session-id")
        @NotBlank(message = "Session ID is required")
        String sessionId,
    @Schema(description = "Prompt or question from the user", example = "Hello?")
        @NotBlank(message = "User query cannot be empty")
        @Size(max = 2000, message = "User query cannot exceed 2000 characters")
        String userQuery) {}
