package com.hieulc.insightragworker.config;

import com.hieulc.insightragworker.config.properties.DebeziumProperties;
import com.hieulc.insightragworker.infrastructure.cdc.debezium.OutboxChangeConsumer;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DebeziumConfig {

  @Bean
  public DebeziumEngine<ChangeEvent<String, String>> debeziumEngine(
      DebeziumProperties properties, OutboxChangeConsumer consumer) {
    return DebeziumEngine.create(Json.class)
        .using(properties.asProperties())
        .notifying(consumer)
        .build();
  }
}
