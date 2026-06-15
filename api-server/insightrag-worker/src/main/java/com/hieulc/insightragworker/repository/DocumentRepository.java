package com.hieulc.insightragworker.repository;

import com.hieulc.insightragworker.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>, DocumentCustomRepository {

//    @Query(
//            value = "INSERT INTO worker_schema.document " +
//            "(id, file_key, status, hash_content, latest_sequencer, created_at, updated_at, acl_role, type) " +
//            "VALUES (:#{#document.id}, :#{#document.fileKey}, :#{#document.status}, :#{#document.hashContent}, :#{#document.latestSequencer}, now(), now(), :#{#document.aclRole}, :#{#document.type}) " +
//            "ON CONFLICT (file_key) DO UPDATE SET " +
//                    "status = EXCLUDED.status, " +
//                    "latest_sequencer = EXCLUDED.latest_sequencer, " +
//                    "updated_at = now(), " +
//                    "type = EXCLUDED.type " +
//                    "WHERE worker_schema.document.latest_sequencer < EXCLUDED.latest_sequencer",
//            nativeQuery = true
//    )
//    int upsertWithSequencerCheck(
//      @Param("document") Document document
//    );

    Optional<Document> findByHashContent(String hashContent);


}
