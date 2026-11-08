package com.hieulc.insightragworker.infrastructure.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hieulc.insightragworker.base.AbstractPostgresIT;
import com.hieulc.insightragworker.document.enums.DocumentAclRole;
import com.hieulc.insightragworker.document.enums.DocumentClassification;
import com.hieulc.insightragworker.document.enums.DocumentStatus;
import com.hieulc.insightragworker.document.model.Document;
import com.hieulc.insightragworker.document.model.DocumentDeleteResult;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@JdbcTest
// not to replace Testcontainer with an in-memory DB
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DocumentCustomRepositoryImpl.class)
class DocumentCustomRepositoryImplIT extends AbstractPostgresIT {

  @Autowired private DocumentCustomRepositoryImpl documentRepository;
  @Autowired private JdbcClient jdbcClient;
  private Document.DocumentBuilder baseDocumentBuilder;

  private final String FILE_KEY = "uploads/document.pdf";

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM worker_schema.document").update();
  }

  @Nested
  class UpsertWithSequencerCheck {

    @BeforeEach
    void setUpDocumentBuilder() {
      baseDocumentBuilder =
          Document.builder()
              .id(UUID.nameUUIDFromBytes(FILE_KEY.getBytes(StandardCharsets.UTF_8)))
              .fileKey(FILE_KEY)
              .status(DocumentStatus.ACTIVE)
              .documentAclRole(DocumentAclRole.PUBLIC)
              .type(DocumentClassification.CLASSIFIED);
    }

    @Test
    void inserts_new_document_and_returns_true() {
      Document newDoc =
          baseDocumentBuilder.hashContent("hash-document").latestSequencer("100").build();

      Optional<Boolean> result = documentRepository.upsertWithSequencerCheck(newDoc);
      assertThat(result).isPresent();
      assertThat(result.get()).isTrue();

      Integer count =
          jdbcClient
              .sql(
                  "SELECT count(*) FROM worker_schema.document WHERE file_key = 'uploads/document.pdf'")
              .query(Integer.class)
              .single();
      assertThat(count).isEqualTo(1);
    }

    @Test
    void ignores_stale_event_and_returns_empty_when_sequencer_is_older_or_equal() {
      Document initialDoc =
          baseDocumentBuilder.hashContent("correct-hash").latestSequencer("100").build();
      documentRepository.upsertWithSequencerCheck(initialDoc);

      Document staleDoc =
          baseDocumentBuilder.hashContent("stale-hash").latestSequencer("050").build();
      Optional<Boolean> result = documentRepository.upsertWithSequencerCheck(staleDoc);

      assertThat(result).isEmpty();
      String currentHash =
          jdbcClient
              .sql(
                  "SELECT hash_content FROM worker_schema.document WHERE file_key = 'uploads/document.pdf'")
              .query(String.class)
              .single();
      assertThat(currentHash).isEqualTo("correct-hash");
    }

    @Test
    void updates_existing_document_and_returns_false_when_sequencer_is_higher() {
      Document initialDoc =
          baseDocumentBuilder.hashContent("old-hash").latestSequencer("100").build();
      documentRepository.upsertWithSequencerCheck(initialDoc);

      Document updatedDoc =
          baseDocumentBuilder
              .id(UUID.randomUUID())
              .hashContent("new-hash")
              .latestSequencer("200")
              .build();

      Optional<Boolean> result = documentRepository.upsertWithSequencerCheck(updatedDoc);

      assertThat(result).isPresent();
      assertThat(result.get()).isFalse();

      String currentHash =
          jdbcClient
              .sql(
                  "SELECT hash_content FROM worker_schema.document WHERE file_key = 'uploads/document.pdf'")
              .query(String.class)
              .single();
      assertThat(currentHash).isEqualTo("new-hash");
    }

    @Test
    void updates_existing_document_when_identical_document_status_is_deleted() {
      Document initialDoc =
          baseDocumentBuilder.hashContent("content-hash").latestSequencer("100").build();
      documentRepository.upsertWithSequencerCheck(initialDoc);

      Optional<DocumentDeleteResult> deleteResult =
          documentRepository.updateSoftDeleteDocument(FILE_KEY, "101");

      assertThat(deleteResult).isPresent();
      assertThat(deleteResult.get().id()).isEqualTo(initialDoc.getId());

      Document duplicateDoc =
          baseDocumentBuilder.hashContent("content-hash").latestSequencer("102").build();
      Optional<Boolean> result = documentRepository.upsertWithSequencerCheck(duplicateDoc);

      assertThat(result).isPresent();
      assertThat(result.get()).isFalse();
    }

    @Test
    void should_throw_exception_on_active_hash_content_collision() {
      Document initialDoc =
          baseDocumentBuilder.hashContent("content-hash").latestSequencer("100").build();
      documentRepository.upsertWithSequencerCheck(initialDoc);
      String updateFilename = FILE_KEY + "_V2";
      Document updateDoc =
          baseDocumentBuilder
              .fileKey(updateFilename)
              .hashContent("content-hash")
              .latestSequencer("100")
              .build();

      assertThrows(
          DataIntegrityViolationException.class,
          () -> documentRepository.upsertWithSequencerCheck(updateDoc));
    }
  }

  @Nested
  class UpdateSoftDeleteDocument {

    private UUID mockDocId;

    @BeforeEach
    void setUpActiveDocument() {
      mockDocId = UUID.randomUUID();
      jdbcClient
          .sql(
              """
              INSERT INTO worker_schema.document
              (id, file_key, status, hash_content, latest_sequencer, created_at, updated_at, acl_role, type)
              VALUES (:id, 'delete-document.pdf', 'ACTIVE', 'hashed-document-content', '100', now(), now(), 'PUBLIC', 'CLASSIFIED')
              """)
          .param("id", mockDocId)
          .update();
    }

    @Test
    void updates_delete_status_when_sequencer_is_higher() {

      Optional<DocumentDeleteResult> result =
          documentRepository.updateSoftDeleteDocument("delete-document.pdf", "200");

      assertThat(result).isPresent();
      assertThat(result.get().id()).isEqualTo(mockDocId);

      String status =
          jdbcClient
              .sql(
                  "SELECT status FROM worker_schema.document WHERE file_key = 'delete-document.pdf'")
              .query(String.class)
              .single();

      assertThat(status).isEqualTo("DELETED");
    }

    @Test
    void updates_delete_status_when_sequencer_is_stale() {

      Optional<DocumentDeleteResult> result =
          documentRepository.updateSoftDeleteDocument("delete-document.pdf", "099");

      assertThat(result).isEmpty();

      String status =
          jdbcClient
              .sql(
                  "SELECT status FROM worker_schema.document WHERE file_key = 'delete-document.pdf'")
              .query(String.class)
              .single();

      assertThat(status).isEqualTo("ACTIVE");
    }
  }

  @Nested
  class FindFileKeyByHash {
    @BeforeEach
    void setUpDocumentBuilder() {
      baseDocumentBuilder =
          Document.builder()
              .id(UUID.randomUUID())
              .fileKey(FILE_KEY)
              .status(DocumentStatus.ACTIVE)
              .documentAclRole(DocumentAclRole.PUBLIC)
              .type(DocumentClassification.CLASSIFIED);
    }

    @Test
    void returns_empty_when_fileKey_is_identical() {
      String hashContent = "hash-content";
      Document initialDoc =
          baseDocumentBuilder.hashContent(hashContent).latestSequencer("100").build();
      documentRepository.upsertWithSequencerCheck(initialDoc);

      Optional<String> result = documentRepository.findFileKeyByHash(hashContent, FILE_KEY);
      assertThat(result).isEmpty();
    }

    @Test
    void returns_empty_when_document_is_deleted() {
      String hashContent = "hash-content";
      Document initialDoc =
          baseDocumentBuilder.hashContent(hashContent).latestSequencer("100").build();
      documentRepository.upsertWithSequencerCheck(initialDoc);
      documentRepository.updateSoftDeleteDocument(FILE_KEY, "101");

      Optional<String> result = documentRepository.findFileKeyByHash(hashContent, FILE_KEY);
      assertThat(result).isEmpty();
    }
  }
}
