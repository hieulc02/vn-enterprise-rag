package com.hieulc.insightragretrieval.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "insightrag.ai.gemini")
@Builder
public record GenAiProperties(
    @NotBlank String baseUrl,
    @NotBlank String modelMain,
    @NotBlank String modelLite,
    @NotBlank String modelTokenizer,
    @NotBlank String apiKey,
    @Min(1) int queryLimitToken,
    @Min(1) int maxHistoryToken,
    @Valid FallbackProperties fallback,
    @Valid MemoryProperties memory) {
  public record FallbackProperties(@Min(1) int inputLimit, @Min(1) int outputLimit) {}

  public record MemoryProperties(@Min(1000) int maxSession, @Min(1) int ttlMinutes) {}
}
