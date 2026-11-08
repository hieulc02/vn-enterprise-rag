package com.hieulc.insightragingestion.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "kafka_dlt_audit", schema = "worker_schema")
@Getter
@Setter
public class KafkaDltAudit {

  @GeneratedValue(strategy = GenerationType.UUID)
  @Id
  private UUID id;

  @Column(name = "message_key", nullable = false)
  private String messageKey;

  @Column(name = "original_topic", nullable = false)
  private String originalTopic;

  @Column(name = "original_partition", nullable = false)
  private Integer originalPartition;

  @Column(name = "original_offset", nullable = false)
  private Long originalOffset;

  @Column(name = "exception_message", nullable = false)
  private String exceptionMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false)
  private String payload;

  @Column(name = "status")
  private String status = "PENDING_REVIEW";

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;
}
