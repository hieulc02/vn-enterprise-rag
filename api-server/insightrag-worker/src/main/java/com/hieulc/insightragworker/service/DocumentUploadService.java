package com.hieulc.insightragworker.service;

import com.hieulc.insightragworker.command.DocumentUploadCommand;
import com.hieulc.insightragworker.command.handler.DocumentUploadHandler;
import com.hieulc.insightragworker.entity.Document;
import com.hieulc.insightragworker.enums.DocumentAclRole;
import com.hieulc.insightragworker.enums.DocumentClassification;
import com.hieulc.insightragworker.enums.DocumentStatus;
import com.hieulc.insightragworker.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadService implements DocumentUploadHandler {

    private final DocumentRepository documentRepository;

    @Override
    public void handle(DocumentUploadCommand command) {

        if(processStaleEvent(command)){
            log.info("Event sequencer {} of file {} is stale" ,command.sequenceId(), command.fileKey());
            return;
        }



    }

    /**
     * In a concurrent environment, this check the event's sequencer against database to prevent
     * out-of-order event processing from overriding newer data
     * @param command the payload command from MinIO bucket notifications
     * @return
     * {@code true} if the event is successfully upserted, {@code false} if the event is stale
     */
    private boolean processStaleEvent(DocumentUploadCommand command){

        DocumentAclRole documentAclRole = DocumentAclRole.fromBucketName(command.bucketName());

        Document tombstoneDocument = Document.builder()
                .id(UUID.randomUUID())
                .fileKey(command.fileKey())
                .status(DocumentStatus.ACTIVE)
                .hashContent(command.hashContent())
                .latestSequencer(command.sequenceId())
                .documentAclRole(documentAclRole)
                .type(DocumentClassification.fromAclRole(documentAclRole))
                .build();

        int upsertRows = documentRepository.upsertWithSequencerCheck(tombstoneDocument);

        return (upsertRows == 0);
    }
}
