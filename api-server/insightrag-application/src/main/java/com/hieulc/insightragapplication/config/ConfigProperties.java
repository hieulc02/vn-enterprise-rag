package com.hieulc.insightragapplication.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application")
public class ConfigProperties {

    @Getter
    @Setter
    public static class S3{
       private String host;
    }
}
