package com.hieulc.insightragworker.repository;

import com.hieulc.insightragworker.entity.Document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentCustomRepository {
    Optional<Boolean> upsertWithSequencerCheck(Document document);

    int addDepartmentsToDocument(UUID documentId, List<String> departmentNames);
    int retainOnlyDocumentDepartments(UUID documentId, List<String> departmentNames);

}
