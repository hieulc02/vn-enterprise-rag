package com.hieulc.insightragretrieval.config;

import com.hieulc.insightragretrieval.service.chat.extractor.QueryAnalyzer;
import com.hieulc.insightragretrieval.service.tokenizer.GeminiTokenEstimatorAdapter;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfig {

  @Value("${insightrag.ai.gemini.model-main}")
  private String modelName;

  @Value("${insightrag.ai.gemini.model-lite}")
  private String liteModelName;

  @Value("${insightrag.ai.gemini.model-tokenizer}")
  private String tokenizerModelName;

  @Value("${insightrag.ai.gemini.api-key}")
  private String apiKey;

  @Value("${insightrag.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
  private String geminiBaseUrl;

  @Bean
  public TokenCountEstimator tokenCountEstimator() {
    return new GeminiTokenEstimatorAdapter(tokenizerModelName);
  }

  @Bean
  @Primary
  public ChatModel mainChatModel() {
    return GoogleGenAiChatModel.builder()
        .apiKey(apiKey)
        .modelName(modelName)
        .temperature(0.0)
        .maxRetries(3)
        .logRequests(true)
        .logResponses(true)
        .build();
  }

  @Bean
  public StreamingChatModel streamingChatModel() {
    return GoogleGenAiStreamingChatModel.builder()
        .apiKey(apiKey)
        .modelName(modelName)
        .temperature(0.0)
        .logRequests(true)
        .logResponses(true)
        .build();
  }

  @Bean
  public ChatModel fastChatModel() {
    return GoogleGenAiChatModel.builder()
        .apiKey(apiKey)
        .modelName(liteModelName)
        .temperature(0.0)
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
        .baseUrl(geminiBaseUrl)
        .defaultHeader("x-goog-api-key", apiKey)
        .requestFactory(requestFactory)
        .build();
  }
}
