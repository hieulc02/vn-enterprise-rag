package com.hieulc.insightragretrieval.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Insight RAG retrieval API")
                .version("1.0")
                .description("API Documentation for GraphRAG-based AI Chat application")
                .contact(new Contact().name("hieulc")));
  }
}
