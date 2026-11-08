package com.hieulc.insightragworker.outbox.model;

import com.hieulc.insightragworker.outbox.enums.AggregateType;
import com.hieulc.insightragworker.outbox.enums.EventType;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_outbox", schema = "worker_schema")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Setter
@Getter
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
      DocumentOutboxPayload payload) {
    this.aggregateId = aggregateId;
    this.aggregateType = aggregateType;
    this.eventType = eventType;
    this.payload = payload;
  }
}
