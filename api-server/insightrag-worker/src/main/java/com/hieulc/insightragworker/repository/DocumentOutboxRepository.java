package com.hieulc.insightragworker.repository;

import com.hieulc.insightragworker.entity.DocumentOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentOutboxRepository extends JpaRepository<DocumentOutbox, UUID> {
    
}
