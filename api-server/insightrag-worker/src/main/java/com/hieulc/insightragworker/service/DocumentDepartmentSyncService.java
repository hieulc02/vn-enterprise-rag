package com.hieulc.insightragworker.service;

import com.hieulc.insightragworker.dto.DocumentId;
import com.hieulc.insightragworker.exception.appli.DocumentIdNotFoundException;
import com.hieulc.insightragworker.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentDepartmentSyncService {

    private final DocumentRepository documentRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    void syncDocumentDepartments(String fileKey, List<String> departmentNames){
        UUID documentId = getDocumentID(fileKey);

        int removedRows = documentRepository.retainOnlyDocumentDepartments(documentId, departmentNames);
        log.debug("Removed {} obsolete departments for document {}", fileKey, removedRows);

        int upsertRows = documentRepository.addDepartmentsToDocument(documentId, departmentNames);
        log.debug("Sync complete for document {}. Added: {} new departments", fileKey, upsertRows);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    void retainDocumentDepartments(String fileKey, List<String> departmentNames){
        UUID documentId = getDocumentID(fileKey);
        int removedRows = documentRepository.retainOnlyDocumentDepartments(documentId, departmentNames);

        log.debug("{} departments of document {} has been removed", removedRows, fileKey);
    }

    private UUID getDocumentID(String fileKey){
        return documentRepository.findByFileKey(fileKey, DocumentId.class)
                .orElseThrow(() -> new DocumentIdNotFoundException(fileKey)).id();
    }

}
