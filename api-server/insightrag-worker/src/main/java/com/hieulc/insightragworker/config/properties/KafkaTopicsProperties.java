package com.hieulc.insightragworker.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Validated
@ConfigurationProperties(prefix = "insightrag.kafka.topics")
public record KafkaTopicsProperties(
      @NotBlank String outboxEvent,
      @NotBlank String dlq,
      @NotBlank String debeziumOffset
) {}
