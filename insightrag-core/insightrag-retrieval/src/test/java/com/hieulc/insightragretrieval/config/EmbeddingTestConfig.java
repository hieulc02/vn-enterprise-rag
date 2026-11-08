package com.hieulc.insightragretrieval.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@TestConfiguration
public class EmbeddingTestConfig {

  @Bean(name = "infinityRestClient")
  public RestClient infinityRestClient(RestClient.Builder builder) {
    return builder.build();
  }
}
