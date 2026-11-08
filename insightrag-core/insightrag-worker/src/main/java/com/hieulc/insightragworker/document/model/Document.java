package com.hieulc.insightragworker.document.model;

import com.hieulc.insightragworker.document.enums.DocumentAclRole;
import com.hieulc.insightragworker.document.enums.DocumentClassification;
import com.hieulc.insightragworker.document.enums.DocumentStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "document", schema = "worker_schema")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Document {

  @Id
  @EqualsAndHashCode.Include
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "file_key", nullable = false, unique = true)
  private String fileKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  private DocumentStatus status;

  @Column(name = "hash_content", nullable = false, length = 64)
  private String hashContent;

  @Column(name = "latest_sequencer", nullable = false, length = 100)
  private String latestSequencer;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "acl_role", nullable = false, length = 50)
  private DocumentAclRole documentAclRole;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private DocumentClassification type;
}
