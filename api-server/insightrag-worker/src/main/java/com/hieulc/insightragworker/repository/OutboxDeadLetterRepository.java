package com.hieulc.insightragworker.repository;

import com.hieulc.insightragworker.entity.OutboxDeadLetterEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxDeadLetterRepository extends JpaRepository<OutboxDeadLetterEvent, UUID> {

}
