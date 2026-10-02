package com.hieulc.insightragretrieval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.hieulc.insightragretrieval.config.properties")
public class InsightragRetrievalApplication {

  public static void main(String[] args) {
    SpringApplication.run(InsightragRetrievalApplication.class, args);
  }
}
