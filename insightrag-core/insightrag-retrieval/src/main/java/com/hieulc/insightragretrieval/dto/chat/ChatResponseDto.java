package com.hieulc.insightragretrieval.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response payload containing the AI's answer")
public record ChatResponseDto(
    @Schema(description = "The session identifier echoing the request") String sessionId,
    @Schema(description = "The generated text response from the LLM") String response) {}
