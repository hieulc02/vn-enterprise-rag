package com.hieulc.insightragretrieval.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("insightrag.embedding")
public record EmbeddingProperties(
    @NotBlank String infinityUrl, @NotBlank String model, @Min(1) long timeoutInSeconds) {}
