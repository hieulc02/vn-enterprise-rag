package com.hieulc.insightragingestion.repository.kafka;

import com.hieulc.insightragingestion.entity.KafkaDltAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KafkaDltAuditRepository extends JpaRepository<KafkaDltAudit, UUID> {}
