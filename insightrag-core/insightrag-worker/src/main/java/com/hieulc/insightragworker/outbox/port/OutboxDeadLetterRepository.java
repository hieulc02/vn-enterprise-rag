package com.hieulc.insightragworker.outbox.port;

import com.hieulc.insightragworker.outbox.model.OutboxDeadLetterEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxDeadLetterRepository extends JpaRepository<OutboxDeadLetterEvent, UUID> {}
