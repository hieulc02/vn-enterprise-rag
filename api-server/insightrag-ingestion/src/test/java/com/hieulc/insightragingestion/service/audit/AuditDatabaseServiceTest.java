package com.hieulc.insightragingestion.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.hieulc.insightragingestion.entity.KafkaDltAudit;
import com.hieulc.insightragingestion.repository.kafka.KafkaDltAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuditDatabaseServiceTest {
  @Mock private KafkaDltAuditRepository auditRepository;

  private AuditDatabaseService auditDatabaseService;

  @BeforeEach
  void setUp() {
    auditDatabaseService = new AuditDatabaseService(auditRepository, new ObjectMapper());
  }

  @Test
  void saveAuditRecord_wraps_poison_pill_in_safe_envelope() {
    String poisonPill = "{\"file_key\": \"corrupted_data";

    auditDatabaseService.saveAuditRecord("key", poisonPill, "error", "topic", 1, 100L);

    ArgumentCaptor<KafkaDltAudit> captor = ArgumentCaptor.forClass(KafkaDltAudit.class);
    verify(auditRepository).saveAndFlush(captor.capture());

    String payload = captor.getValue().getPayload();

    assertThat(payload).contains("malformedPayload");
    assertThat(payload).contains("corrupted_data");

    assertThatCode(() -> new ObjectMapper().readTree(payload)).doesNotThrowAnyException();
  }

  @Test
  void saveAuditRecord_swallows_data_integrity_violation_for_idempotency() {
    String validJson = "{\"key\": \"ok\"}";

    doThrow(DataIntegrityViolationException.class).when(auditRepository).saveAndFlush(any());

    assertThatCode(
            () -> auditDatabaseService.saveAuditRecord("key", validJson, null, "topic", 1, 100L))
        .doesNotThrowAnyException();
  }
}
