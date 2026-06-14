package com.hieulc.insightragworker.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class MinIOConfig {

    @Value("${minio.endpoint}")
    private String minIOEndpoint;
    @Value("${minio.credentials.username}")
    private String minIOUsername;
    @Value("${minio.credentials.password}")
    private String minIOPassword;

    @Bean
    MinioClient minioClient(){
        return MinioClient.builder()
                .endpoint(minIOEndpoint)
                .credentials(minIOUsername, minIOPassword)
                .build();
    }
}
