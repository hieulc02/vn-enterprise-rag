package com.hieulc.insightragworker.repository;

import com.hieulc.insightragworker.entity.Document;
import com.hieulc.insightragworker.enums.DocumentAclRole;
import com.hieulc.insightragworker.enums.DocumentClassification;
import com.hieulc.insightragworker.enums.DocumentStatus;
import com.hieulc.insightragworker.enums.DocumentTags;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.*;

import static org.assertj.core.api.Assertions.*;


@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DocumentRepositoryTest {

    static DockerImageName dockerImageName = DockerImageName.parse("pgvector/pgvector")
            .withTag("pg16")
            .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(dockerImageName);

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void shouldInsertNewDocumentAndReturnTrue(){

        Document documentTest = createTestDocument("bucket/file1.pdf", "147279EAF9F40933");

        Optional<Boolean> upsertCheck = documentRepository.upsertWithSequencerCheck(documentTest);

        assertThat(upsertCheck).isPresent();
        assertThat(upsertCheck.get()).isTrue();
    }

    @Test
    void shouldUpdateExistingDocumentIfLateSequencerIsGreaterAndReturnFalse(){

        String fileKey = "bucket/file2.pdf";
        String sequencer1 = "17A9AB4FA93B35D8";
        String sequencer2 = "17A9AB4FA93B36E2";

        Document documentTest1 = createTestDocument(fileKey, sequencer1);
        documentRepository.upsertWithSequencerCheck(documentTest1);

        Document documentTest2 = createTestDocument(fileKey, sequencer2);
        Optional<Boolean> upsertCheck = documentRepository.upsertWithSequencerCheck(documentTest2);

        assertThat(upsertCheck).isPresent();
        assertThat(upsertCheck.get()).isFalse();
    }

    @Test
    void shouldIgnoreStaleEventIfLateSequencerIsLesserAndReturnEmpty(){

        String fileKey = "bucket/file3.pdf";
        String sequencer1 = "17A9AB4FA93B36E2";
        String sequencer2 = "17A9AB4FA93B35D8";

        Document documentTest1 = createTestDocument(fileKey, sequencer1);
        documentRepository.upsertWithSequencerCheck(documentTest1);

        Document documentTest2 = createTestDocument(fileKey, sequencer2);
        Optional<Boolean> upsertCheck = documentRepository.upsertWithSequencerCheck(documentTest2);

        assertThat(upsertCheck).isEmpty();
    }

    @Test
    void shouldInsertOrIgnoreDocumentDepartmentAndReturnModifiedRows(){

        String fileKey = "bucket/file4.pdf";
        String sequencer = "17A9AB4FA93B36E2";

        Document documentTest1 = createTestDocument(fileKey, sequencer);
        documentRepository.upsertWithSequencerCheck(documentTest1);

        List<String> departmentNames = Arrays.asList("IT", "ACCOUNTING", "HR");
        int addedRows = documentRepository.addDepartmentsToDocument(documentTest1.getId(), departmentNames);

        assertThat(addedRows).isEqualTo(3);

        List<String> updatedDepartmentNames = Arrays.asList("THE_BOARD", "MARKETING", "IT", "ACCOUNTING");
        int modifiedRows = documentRepository.addDepartmentsToDocument(documentTest1.getId(), updatedDepartmentNames);
        assertThat(modifiedRows).isEqualTo(2);
    }

    @Test
    void shouldRetainOrWipeUpDocumentDepartmentAndReturnRemovedRows(){
        String fileKey = "bucket/file5.pdf";
        String sequencer = "17A9AB4FA93B36E2";

        Document documentTest1 = createTestDocument(fileKey, sequencer);
        documentRepository.upsertWithSequencerCheck(documentTest1);

        List<String> departmentNames = Arrays.asList("IT", "ACCOUNTING", "HR");
        documentRepository.addDepartmentsToDocument(documentTest1.getId(), departmentNames);

        List<String> removedDepartment = Arrays.asList("ACCOUNTING", "HR");
        int removedRows = documentRepository.retainOnlyDocumentDepartments(documentTest1.getId(), removedDepartment);
        assertThat(removedRows).isEqualTo(1);

        List<String> emptyDepartment = Collections.emptyList();
        int wipeRows = documentRepository.retainOnlyDocumentDepartments(documentTest1.getId(), emptyDepartment);
        assertThat(wipeRows).isEqualTo(2);
    }



    private Document createTestDocument(String fileKey, String sequencer){
        return Document.builder()
                .id(UUID.randomUUID())
                .fileKey(fileKey)
                .status(DocumentStatus.ACTIVE)
                .latestSequencer(sequencer)
                .hashContent("hashContent-"+sequencer)
                .documentAclRole(DocumentAclRole.PUBLIC)
                .type(DocumentClassification.CLASSIFIED)
                .build();
    }

}