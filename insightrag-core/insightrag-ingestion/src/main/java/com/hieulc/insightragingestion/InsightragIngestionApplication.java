package com.hieulc.insightragingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = {"com.hieulc.insightragingestion", "com.hieulc.coreinfrastructure"})
public class InsightragIngestionApplication {

  public static void main(String[] args) {
    SpringApplication.run(InsightragIngestionApplication.class, args);
  }
}
