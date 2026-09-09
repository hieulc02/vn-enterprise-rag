package com.hieulc.insightragingestion.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeadLetterTopicConsumer {

  @KafkaListener(topics = "${insightrag.kafka.topics.consumer}-dlt")
  public void consumeDlt(
      String payload,
      @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
      @Header(name = "kafka_dlt-exception-message", required = false) String exceptionMsg) {
    log.error("Message {} routed to DLT. Reason: {}", key, exceptionMsg);
    // database auditing
  }
}
