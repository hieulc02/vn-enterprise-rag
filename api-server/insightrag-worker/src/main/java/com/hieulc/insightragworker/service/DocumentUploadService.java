package com.hieulc.insightragworker.service;

import com.hieulc.insightragworker.command.DocumentUploadCommand;
import com.hieulc.insightragworker.command.handler.DocumentUploadHandler;
import com.hieulc.insightragworker.dto.DocumentOutboxPayload;
import com.hieulc.insightragworker.entity.Document;
import com.hieulc.insightragworker.entity.DocumentOutbox;
import com.hieulc.insightragworker.enums.*;
import com.hieulc.insightragworker.repository.DocumentOutboxRepository;
import com.hieulc.insightragworker.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadService implements DocumentUploadHandler {

    private final DocumentRepository documentRepository;
    private final DocumentOutboxRepository documentOutboxRepository;

    @Override
    @Transactional
    public void handle(DocumentUploadCommand command) {

        DocumentAclRole documentAclRole = DocumentAclRole.fromBucketName(command.bucketName());

        Document document = Document.builder()
                .id(UUID.randomUUID())
                .fileKey(command.fileKey())
                .status(DocumentStatus.ACTIVE)
                .hashContent(command.hashContent())
                .latestSequencer(command.sequenceId())
                .documentAclRole(documentAclRole)
                .type(DocumentClassification.fromAclRole(documentAclRole))
                .build();

        log.debug("Event sequencer of file key {}: {}", command.fileKey(), command.sequenceId());
        Optional<Boolean> upsertRows = documentRepository.upsertWithSequencerCheck(document);

        if(upsertRows.isEmpty()){
            log.info("Event sequencer {} of file {} is already stale", command.sequenceId(), command.fileKey());
            return;
        }

        boolean isNewDocument = upsertRows.get();

        DocumentOutbox outboxEvent =
                DocumentOutbox.builder()
                        .aggregateType(AggregateType.DOCUMENT)
                        .aggregateId(document.getId().toString())
                        .eventType(EventType.isNewOrUpdated(isNewDocument))
                        .payload(newPayload(command))
                        .build();

        documentOutboxRepository.save(outboxEvent);

        //only for CDC to catch, prevent WAL bloats
        documentOutboxRepository.delete(outboxEvent);

    }

    private DocumentOutboxPayload newPayload(DocumentUploadCommand command){
        return new DocumentOutboxPayload(command.fileKey(), command.bucketName());
    }

}
