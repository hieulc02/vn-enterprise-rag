package com.hieulc.insightragworker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_dlq", schema = "worker_schema")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OutboxDeadLetterEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "failed_payload", nullable = false, columnDefinition = "text")
    private String failedPayload;

    @Column(name = "error_reason", nullable = false, columnDefinition = "text")
    private String errorReason;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "is_resolved")
    private boolean isResolved = false;

    public OutboxDeadLetterEvent(String failedPayload, String errorReason) {
        this.failedPayload = failedPayload;
        this.errorReason = errorReason;
        this.isResolved = false;
    }
}
