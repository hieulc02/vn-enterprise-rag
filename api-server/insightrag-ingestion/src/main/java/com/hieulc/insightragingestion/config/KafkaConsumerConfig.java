package com.hieulc.insightragingestion.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration
public class KafkaConsumerConfig {

  @Value("${insightrag.kafka.topics.final-dlq}")
  private String finalDlq;

  @Bean
  public JacksonJsonMessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  @Bean
  public DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> kafkaTemplate) {

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (ConsumerRecord<?, ?> record, Exception exception) -> {
              String dlTopic = record.topic() + "-dlt";

              if (record.topic().endsWith("-dlt")) {
                return new TopicPartition(finalDlq, -1);
              }
              // Set partition to -1 to let the Kafka broker determine the partition routing for the
              // DLT, preventing out-of-bounds partition errors
              return new TopicPartition(dlTopic, -1);
            });

    FixedBackOff fixedBackOff = new FixedBackOff(2000L, 3);
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, fixedBackOff);
    // Fatal exception from Jackson, must route to DLQ
    errorHandler.addNotRetryableExceptions(
        JacksonException.class,
        IllegalArgumentException.class,
        InvalidDataAccessResourceUsageException.class);
    return errorHandler;
  }
}
