package com.hieulc.insightragworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InsightragWorkerApplication {

    public static void main(String[] args) {
            SpringApplication.run(InsightragWorkerApplication.class, args);
    }

}
