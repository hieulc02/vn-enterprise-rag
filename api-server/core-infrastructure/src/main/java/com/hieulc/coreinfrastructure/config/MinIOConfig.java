package com.hieulc.coreinfrastructure.config;

import com.hieulc.coreinfrastructure.config.properties.MinIOProperties;
import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinIOProperties.class)
public class MinIOConfig {

  @Bean
  MinioClient minioClient(MinIOProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.credentials().username(), properties.credentials().password())
        .build();
  }
}
