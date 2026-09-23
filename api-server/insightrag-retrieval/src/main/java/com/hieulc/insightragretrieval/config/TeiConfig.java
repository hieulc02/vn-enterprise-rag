package com.hieulc.insightragretrieval.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class TeiConfig {

  @Value("${insightrag.tei.url}")
  private String teiBaseUrl;

  @Bean
  public RestClient teiRestClient(RestClient.Builder builder) {
    return builder
        .baseUrl(teiBaseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
}
