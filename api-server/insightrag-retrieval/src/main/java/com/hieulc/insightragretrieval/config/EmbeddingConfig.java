package com.hieulc.insightragretrieval.config;

import com.hieulc.insightragretrieval.config.properties.EmbeddingProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class EmbeddingConfig {

  private final EmbeddingProperties embeddingProperties;

  @Bean(name = "infinityRestClient")
  public RestClient infinityRestClient(RestClient.Builder builder) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(60));
    return builder
        .baseUrl(embeddingProperties.infinityUrl())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .requestFactory(requestFactory)
        .build();
  }
}
