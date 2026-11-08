package com.hieulc.insightragingestion.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaDeadLetterTopicConfig {

  @Bean(name = "deadLetterTopicContainerFactory")
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>>
      deadLetterTopicContainerFactory(ConsumerFactory<Object, Object> defaultConsumerFactory) {
    Map<String, Object> props = new HashMap<>(defaultConsumerFactory.getConfigurationProperties());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    ConsumerFactory<String, String> dltConsumerFactory = new DefaultKafkaConsumerFactory<>(props);

    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(dltConsumerFactory);
    // enforce manual ack exclusively for the DLT
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

    return factory;
  }
}
