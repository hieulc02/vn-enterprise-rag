package com.hieulc.insightragworker.repository;

import com.hieulc.insightragworker.entity.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public int addDepartmentsToDocument(UUID documentId, List<String> departmentNames) {
        if(departmentNames == null || departmentNames.isEmpty()){
            return 0;
        }

        String sql = """
                    INSERT INTO worker_schema.document_departments (document_id, department_id)
                        SELECT :documentId, d.id
                          FROM worker_schema.department d
                          WHERE d.name = ANY(:departmentNames::varchar[])
                        ON CONFLICT (document_id, department_id) DO NOTHING
                """;

        return jdbcClient.sql(sql)
                .param("documentId", documentId)
                .param("departmentNames", departmentNames.toArray(new String[0]))
                .update();
    }

    @Override
    public int retainOnlyDocumentDepartments(UUID documentId, List<String> departmentNamesToKeep) {
        if(departmentNamesToKeep == null || departmentNamesToKeep.isEmpty()){
            String wipeSql = """
                    DELETE FROM worker_schema.document_departments WHERE document_id = :documentId
                    """;
            return jdbcClient.sql(wipeSql)
                    .param("documentId", documentId)
                    .update();
        }

        String sql = """
                WITH department_ids AS (
                    SELECT id FROM worker_schema.department
                        WHERE name = ANY(:departmentNames::varchar[])
                )
                
                DELETE FROM worker_schema.document_departments
                WHERE document_id = :documentId
                  AND department_id NOT IN (SELECT id FROM department_ids)
                """;

        return jdbcClient.sql(sql)
                .param("documentId", documentId)
                .param("departmentNames", departmentNamesToKeep.toArray(new String[0]))
                .update();
    }
}
