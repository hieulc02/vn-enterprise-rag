package com.hieulc.insightragworker.service;

import com.hieulc.insightragworker.command.DocumentTaggingCommand;
import com.hieulc.insightragworker.command.handler.DocumentTaggingHandler;
import com.hieulc.insightragworker.dto.DocumentId;
import com.hieulc.insightragworker.enums.DocumentAclRole;
import com.hieulc.insightragworker.enums.DocumentTags;
import com.hieulc.insightragworker.exception.appli.DepartmentInvalidException;
import com.hieulc.insightragworker.exception.appli.DocumentIdNotFoundException;
import com.hieulc.insightragworker.exception.infra.StorageProviderException;
import com.hieulc.insightragworker.repository.DepartmentRepository;
import com.hieulc.insightragworker.repository.DocumentRepository;
import com.hieulc.insightragworker.validation.DepartmentValidationCache;
import io.minio.GetObjectTagsArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import io.minio.messages.Tags;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentTaggingService implements DocumentTaggingHandler {

    private final MinioClient minioClient;
    private final DocumentRepository documentRepository;
    private final DocumentDepartmentSyncService documentDepartmentSyncService;
    private final DepartmentValidationCache departmentValidationCache;

    @Override
    @Retryable(includes = DocumentIdNotFoundException.class, multiplier = 2, delay = 100, maxDelay = 1000, jitter = 10)
    public void handle(DocumentTaggingCommand documentTaggingCommand) {

        Tags documentTags = getS3ObjectTags(documentTaggingCommand);
        Map<String, String> documentTagList = documentTags.get();
        String fileKey = documentTaggingCommand.fileKey();

        processDocumentDepartmentTags(fileKey, documentTagList);

        processDocumentAclRoleTags(fileKey, documentTagList);

    }

    private Tags getS3ObjectTags(DocumentTaggingCommand documentTaggingCommand){
        try {
            return minioClient.getObjectTags(
                    GetObjectTagsArgs.builder()
                            .bucket(documentTaggingCommand.bucketName())
                            .object(documentTaggingCommand.fileKey())
                            .build()
            );
        } catch (MinioException e) {
            throw new StorageProviderException("Failed to get document tags for file: " + documentTaggingCommand.fileKey(), e);
        }
    }

    private void processDocumentDepartmentTags(String fileKey, Map<String, String> documentTags){

        boolean isTagDepartmentsExists = documentTags.containsKey(DocumentTags.DEPARTMENTS.toString());
        if(!isTagDepartmentsExists){
            log.debug("Tags attached with the document {} for department has not been defined", fileKey);
            return;
        }

        String departmentsString = documentTags.get(DocumentTags.DEPARTMENTS.toString());

        //Compliant tag naming rule: DEPARTMENTS(key):IT/ACCOUNTING/HR/MARKETING/THE_BROAD(label)
        List<String> documentDepartmentList = documentParser(departmentsString);

        if(documentDepartmentList.isEmpty()){
            log.debug("The labels of department tag of the document {} has not been defined yet or leaved empty", fileKey);
            documentDepartmentSyncService.retainDocumentDepartments(fileKey, documentDepartmentList);
            return;
        }

        for(String documentDepartment : documentDepartmentList){
            if(!departmentValidationCache.isValid(documentDepartment)){
                throw new DepartmentInvalidException(fileKey, documentDepartment);
            }
        }

        documentDepartmentSyncService.syncDocumentDepartments(fileKey, documentDepartmentList);
    }

    private void processDocumentAclRoleTags(String fileKey, Map<String, String> documentTags){

        boolean isTagAclExists = documentTags.containsKey(DocumentTags.ACL_ROLES.toString());
        if(!isTagAclExists){
            log.debug("Tags attached with the document {} for ACL roles has not been defined", fileKey);
            return;
        }

        String aclRole = documentTags.get(DocumentTags.ACL_ROLES.toString());

//        DocumentAclRole.fromDocumentTag(aclRole);
    }

    private List<String> documentParser(String departmentsString){
        if(departmentsString == null || departmentsString.isBlank())
            return Collections.emptyList();

        return Arrays.stream(departmentsString.split("/"))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .toList();
    }

}
