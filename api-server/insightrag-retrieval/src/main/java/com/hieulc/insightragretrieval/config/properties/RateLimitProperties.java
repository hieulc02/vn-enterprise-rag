package com.hieulc.insightragretrieval.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "insightrag.rate-limit")
@Validated
public record RateLimitProperties(
    @Min(1) int capacity,
    @Min(1) int refillTokens,
    @NotNull Duration refillDuration,
    @Min(100) int cacheMaxSize,
    @NotNull Duration cacheTtl) {}
