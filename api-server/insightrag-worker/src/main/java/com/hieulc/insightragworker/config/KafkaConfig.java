package com.hieulc.insightragworker.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String OUTBOX_TOPIC = "rag-cdc-topic";

    @Bean
    public NewTopic topic(){
        return TopicBuilder.name(OUTBOX_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
