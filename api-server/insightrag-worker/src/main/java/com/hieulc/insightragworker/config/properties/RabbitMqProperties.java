package com.hieulc.insightragworker.config.properties;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("insightrag.rabbitmq")
@Builder
public record RabbitMqProperties(
    String dlQueue,
    String dlExchange,
    String dlRoutingKey,
    String mainQueue,
    String mainExchange,
    String mainRoutingKey) {}
