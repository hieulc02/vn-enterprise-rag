package com.hieulc.insightragworker.entity;

import com.hieulc.insightragworker.dto.DocumentOutboxPayload;
import com.hieulc.insightragworker.enums.AggregateType;
import com.hieulc.insightragworker.enums.EventType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "document_outbox", schema = "worker_schema")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Setter
public class DocumentOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false)
    private AggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private DocumentOutboxPayload payload;

    @Builder
    public DocumentOutbox(
            AggregateType aggregateType,
            String aggregateId,
            EventType eventType,
            DocumentOutboxPayload payload
    ){
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
    }

}
