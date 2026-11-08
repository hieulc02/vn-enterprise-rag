package com.hieulc.insightragingestion.service.audit;

import com.hieulc.insightragingestion.entity.KafkaDltAudit;
import com.hieulc.insightragingestion.repository.kafka.KafkaDltAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditDatabaseService {

  private final KafkaDltAuditRepository auditRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public void saveAuditRecord(
      String key,
      String rawPayload,
      String exceptionMsg,
      String originalTopic,
      Integer originalPartition,
      Long originalOffset) {
    KafkaDltAudit audit = new KafkaDltAudit();
    audit.setMessageKey(key);
    audit.setOriginalTopic(originalTopic);
    audit.setOriginalPartition(originalPartition);
    audit.setOriginalOffset(originalOffset);
    audit.setExceptionMessage(exceptionMsg);

    String jsonPayload;
    try {
      objectMapper.readTree(rawPayload);
      jsonPayload = rawPayload;
    } catch (JacksonException e) {
      log.warn("Malformed payload json detected for topic {}", originalTopic);
      jsonPayload = objectMapper.createObjectNode().put("malformedPayload", rawPayload).toString();
    }

    audit.setPayload(jsonPayload);

    try {
      auditRepository.saveAndFlush(audit);
    } catch (Exception e) {
      log.info(
          "Audit record for topic [{}] partition [{}] offset [{}] already exists",
          originalTopic,
          originalPartition,
          originalOffset);
    }
  }
}
