package com.hieulc.insightragworker.repository;

import com.hieulc.insightragworker.entity.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DocumentCustomRepositoryImpl implements DocumentCustomRepository {
    private final JdbcClient jdbcClient;

    @Override
    public Optional<Boolean> upsertWithSequencerCheck(Document document) {
        String sql = """
                INSERT INTO worker_schema.document\s
                (id, file_key, status, hash_content, latest_sequencer, created_at, updated_at, acl_role, type)\s
                VALUES (:id, :fileKey, :status, :hashContent, :sequencer, now(), now(), :aclRole, :type)\s
                ON CONFLICT (file_key) DO UPDATE SET\s
                   status = EXCLUDED.status,\s
                   hash_content = EXCLUDED.hash_content,\s
                   latest_sequencer = EXCLUDED.latest_sequencer,\s
                   updated_at = now(),\s
                   type = EXCLUDED.type\s
                WHERE worker_schema.document.latest_sequencer < EXCLUDED.latest_sequencer\s
                RETURNING (xmax = 0)
                """;
        return jdbcClient.sql(sql)
                .param("id", document.getId())
                .param("fileKey", document.getStatus())
                .param("status", document.getStatus())
                .param("hashContent", document.getHashContent())
                .param("sequencer", document.getLatestSequencer())
                .param("aclRole", document.getDocumentAclRole().name())
                .param("type", document.getType().name())
                .query(Boolean.class)
                .optional();
    }
}
