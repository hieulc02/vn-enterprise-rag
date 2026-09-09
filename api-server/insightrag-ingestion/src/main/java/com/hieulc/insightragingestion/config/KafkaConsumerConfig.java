package com.hieulc.insightragingestion.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration
public class KafkaConsumerConfig {
  @Bean
  public JacksonJsonMessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  @Bean
  public DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> kafkaTemplate) {

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> {
              String dlTopic = record.topic() + "-dlt";
              return new TopicPartition(dlTopic, record.partition());
            });

    FixedBackOff fixedBackOff = new FixedBackOff(2000L, 3);
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, fixedBackOff);

    // Fatal exception from Jackson, must route to DLQ
    errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, JacksonException.class);
    return errorHandler;
  }
}
