package com.hieulc.insightragingestion.kafka;

import com.hieulc.insightragingestion.service.audit.AuditDatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeadLetterTopicConsumer {

  private final AuditDatabaseService auditDatabaseService;

  @KafkaListener(
      topics = "${insightrag.kafka.topics.consumer}-dlt",
      groupId = "dlt-audit-group",
      containerFactory = "deadLetterTopicContainerFactory")
  public void consumeDlt(
      ConsumerRecord<String, String> payload,
      Acknowledgment acknowledgment,
      @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
      @Header(name = "kafka_dlt-exception-message", required = false) String exceptionMsg,
      @Header(name = "kafka_dlt-original-topic", required = false) String originalTopic,
      @Header(name = "kafka_dlt-original-partition", required = false) Integer originalPartition,
      @Header(name = "kafka_dlt-original-offset", required = false) Long originalOffset) {

    log.error(
        "DLT Alert: Message [{}] from topic [{}] routed to DLT. Reason {}",
        key,
        originalTopic,
        exceptionMsg);
    try {
      // database auditing
      auditDatabaseService.saveAuditRecord(
          key, payload.value(), exceptionMsg, originalTopic, originalPartition, originalOffset);

      acknowledgment.acknowledge();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
