package com.hieulc.insightragretrieval.config;

import com.hieulc.insightragretrieval.config.properties.GenAiProperties;
import com.hieulc.insightragretrieval.service.chat.extractor.QueryAnalyzer;
import com.hieulc.insightragretrieval.service.tokenizer.GeminiTokenEstimatorAdapter;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

  private final GenAiProperties genAiProperties;

  @Bean
  public TokenCountEstimator tokenCountEstimator() {
    return new GeminiTokenEstimatorAdapter(genAiProperties.modelTokenizer());
  }

  @Bean
  @Primary
  public ChatModel mainChatModel() {
    return GoogleGenAiChatModel.builder()
        .apiKey(genAiProperties.apiKey())
        .modelName(genAiProperties.modelMain())
        .temperature(0.0)
        .maxRetries(3)
        .logRequests(true)
        .logResponses(true)
        .build();
  }

  @Bean
  public StreamingChatModel streamingChatModel() {
    return GoogleGenAiStreamingChatModel.builder()
        .apiKey(genAiProperties.apiKey())
        .modelName(genAiProperties.modelMain())
        .temperature(0.0)
        .logRequests(true)
        .logResponses(true)
        .build();
  }

  @Bean
  public ChatModel fastChatModel() {
    return GoogleGenAiChatModel.builder()
        .apiKey(genAiProperties.apiKey())
        .modelName(genAiProperties.modelLite())
        .temperature(0.0)
        .logRequests(true)
        .logResponses(true)
        .maxRetries(3)
        .build();
  }

  @Bean
  public QueryAnalyzer queryAnalyzer(@Qualifier("fastChatModel") ChatModel fastChatModel) {
    return AiServices.builder(QueryAnalyzer.class).chatModel(fastChatModel).build();
  }

  @Bean(name = "geminiRestClient")
  public RestClient geminiRestClient(RestClient.Builder builder) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    return builder
        .baseUrl(genAiProperties.baseUrl())
        .defaultHeader("x-goog-api-key", genAiProperties.apiKey())
        .requestFactory(requestFactory)
        .build();
  }
}
