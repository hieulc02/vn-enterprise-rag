package com.hieulc.insightragingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@TestConfiguration
public class KafkaTestConfig {

  @Bean
  public NewTopic mainTestTopic(@Value("${insightrag.kafka.topics.consumer}") String topic) {
    return TopicBuilder.name(topic).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic dltTestTopic(@Value("${insightrag.kafka.topics.consumer}") String topic) {
    return TopicBuilder.name(topic + "-dlt").partitions(1).replicas(1).build();
  }
}
