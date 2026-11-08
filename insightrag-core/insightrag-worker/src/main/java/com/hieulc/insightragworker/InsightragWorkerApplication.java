package com.hieulc.insightragworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(
    scanBasePackages = {"com.hieulc.insightragworker", "com.hieulc.coreinfrastructure"})
@ConfigurationPropertiesScan("com.hieulc.insightragworker.config.properties")
public class InsightragWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(InsightragWorkerApplication.class, args);
  }
}
