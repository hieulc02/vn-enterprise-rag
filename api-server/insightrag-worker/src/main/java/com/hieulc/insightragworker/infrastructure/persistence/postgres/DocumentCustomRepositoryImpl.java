package com.hieulc.insightragworker.infrastructure.persistence.postgres;

import com.hieulc.insightragworker.document.model.Document;
import com.hieulc.insightragworker.document.model.DocumentDeleteResult;
import com.hieulc.insightragworker.document.port.DocumentCustomRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class DocumentCustomRepositoryImpl implements DocumentCustomRepository {
  private final JdbcClient jdbcClient;

  @Override
  public Optional<Boolean> upsertWithSequencerCheck(Document document) {
    String sql =
        """
          INSERT INTO worker_schema.document
          (id, file_key, status, hash_content, latest_sequencer, created_at, updated_at, acl_role, type)
          VALUES (:id, :fileKey, :status, :hashContent, :sequencer, now(), now(), :aclRole, :type)
          ON CONFLICT (file_key) WHERE worker_schema.document.status != 'DELETED' DO UPDATE SET
             status = EXCLUDED.status,
             hash_content = EXCLUDED.hash_content,
             latest_sequencer = EXCLUDED.latest_sequencer,
             updated_at = now(),
             type = EXCLUDED.type
          WHERE worker_schema.document.latest_sequencer < EXCLUDED.latest_sequencer
          RETURNING (xmax = 0)
          """;
    return jdbcClient
        .sql(sql)
        .param("id", document.getId())
        .param("fileKey", document.getFileKey())
        .param("status", document.getStatus().name())
        .param("hashContent", document.getHashContent())
        .param("sequencer", document.getLatestSequencer())
        .param("aclRole", document.getDocumentAclRole().name())
        .param("type", document.getType().name())
        .query(Boolean.class)
        .optional();
  }

  @Override
  public Optional<DocumentDeleteResult> updateSoftDeleteDocument(String fileKey, String sequencer) {
    String sql =
        """
        UPDATE worker_schema.document
        SET status = 'DELETED',
            latest_sequencer = :sequencer,
            updated_at = now()
        WHERE file_key = :fileKey
          AND status != 'DELETED'
          AND (latest_sequencer IS NULL OR latest_sequencer < :sequencer)
        RETURNING id
      """;

    return jdbcClient
        .sql(sql)
        .param("fileKey", fileKey)
        .param("sequencer", sequencer)
        .query(DocumentDeleteResult.class)
        .optional();
  }

  @Override
  public Optional<String> findFileKeyByHash(String hashContent, String currentFileKey) {
    String sql =
        """
        SELECT file_key
        FROM worker_schema.document
        WHERE hash_content = :hashContent
          AND status != 'DELETED'
        LIMIT 1
        """;

    return jdbcClient
        .sql(sql)
        .param("hashContent", hashContent)
        //        .param("currentFileKey", currentFileKey)
        .query(String.class)
        .optional();
  }
}
