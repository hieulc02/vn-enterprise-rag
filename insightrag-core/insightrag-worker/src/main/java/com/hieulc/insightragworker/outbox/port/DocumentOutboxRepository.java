package com.hieulc.insightragworker.outbox.port;

import com.hieulc.insightragworker.outbox.model.DocumentOutbox;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentOutboxRepository extends JpaRepository<DocumentOutbox, UUID> {}
