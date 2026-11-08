package com.hieulc.insightragworker.document.command;

import com.hieulc.insightragworker.common.command.CommandHandler;
import com.hieulc.insightragworker.config.properties.DocumentValidationProperties;
import com.hieulc.insightragworker.document.enums.DocumentAclRole;
import com.hieulc.insightragworker.document.enums.DocumentClassification;
import com.hieulc.insightragworker.document.enums.DocumentStatus;
import com.hieulc.insightragworker.document.model.*;
import com.hieulc.insightragworker.document.port.DocumentCustomRepository;
import com.hieulc.insightragworker.document.port.DocumentStorage;
import com.hieulc.insightragworker.document.validation.DocumentValidationService;
import com.hieulc.insightragworker.outbox.enums.AggregateType;
import com.hieulc.insightragworker.outbox.enums.EventType;
import com.hieulc.insightragworker.outbox.model.DocumentOutbox;
import com.hieulc.insightragworker.outbox.model.DocumentOutboxPayload;
import com.hieulc.insightragworker.outbox.port.DocumentOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

@Service
@RequiredArgsConstructor
@Slf4j
class DocumentUploadCommandHandler implements CommandHandler<DocumentUploadCommand> {

  private final DocumentCustomRepository documentCustomRepository;
  private final DocumentOutboxRepository documentOutboxRepository;

  private final DocumentStorage documentStorage;
  private final DocumentValidationService documentValidationService;
  private final DocumentValidationProperties properties;

  @Override
  @Transactional
  public void handle(DocumentUploadCommand command) {
    double documentSizeMb = (double) command.sizeBytes() / DataSize.ofMegabytes(1).toBytes();
    if (!documentValidationService.isValidDocument(command.contentType(), documentSizeMb)) {
      String supportDocumentTypes = String.join(", ", properties.allowedContentTypes());
      throw new IllegalArgumentException(
          String.format(
              "Invalid document file [%s] type [%s] or size [%.2f MB]. Supported Document Types: [%s] and Size Limit: [%.2f MB]",
              command.fileKey(),
              command.contentType(),
              documentSizeMb,
              supportDocumentTypes,
              properties.maxFileSizeMb()));
    }

    Document document = createDocument(command, documentSizeMb);

    Optional<String> existingFileKey =
        documentCustomRepository.findFileKeyByHash(document.getHashContent(), command.fileKey());
    if (existingFileKey.isPresent()) {
      // If two identical files with different keys are uploaded at the exact same time.
      // The pre-check might pass for both, but the database constraint will catch the second one.
      log.info(
          "Duplicated content detected. Content for new file {} already exists under active file {}. Skipping upsert and graph extraction.",
          command.fileKey(),
          existingFileKey.get());
      return;
    }

    log.debug("Event sequencer of file key {}: {}", command.fileKey(), command.sequenceId());
    Optional<Boolean> upsertRows;

    try {
      upsertRows = documentCustomRepository.upsertWithSequencerCheck(document);
    } catch (DataIntegrityViolationException e) {
      log.warn(
          "Race condition caught. Hash content collision for file {} during UPSERT",
          command.fileKey());
      return;
    }

    if (upsertRows.isEmpty()) {
      log.info(
          "Event sequencer {} of file {} is already stale",
          command.sequenceId(),
          command.fileKey());
      return;
    }

    boolean isNew = upsertRows.get();
    DocumentOutbox outboxEvent = createDocumentOutbox(command, document, isNew);
    persistAndDeleteOutboxEvent(outboxEvent);
  }

  private Document createDocument(DocumentUploadCommand command, double documentSizeMb) {
    String hashContent = determineHashContent(command, documentSizeMb);
    DocumentAclRole documentAclRole = DocumentAclRole.fromBucketName(command.bucketName());
    UUID documentId = UUID.nameUUIDFromBytes(command.fileKey().getBytes(StandardCharsets.UTF_8));

    return Document.builder()
        .id(documentId)
        .fileKey(command.fileKey())
        .status(DocumentStatus.ACTIVE)
        .hashContent(hashContent)
        .latestSequencer(command.sequenceId())
        .documentAclRole(documentAclRole)
        .type(DocumentClassification.fromAclRole(documentAclRole))
        .build();
  }

  private String determineHashContent(DocumentUploadCommand command, double documentSizeMb) {
    boolean isMultipartETag = command.eTag() != null && command.eTag().contains("-");

    if (isMultipartETag || documentSizeMb > properties.eTagThresholdMb()) {
      log.warn(
          "Hash metadata missing for {}. Falling back to server-side streaming SHA-256 calculation. Multipart ETag: {}, Size: {}MB",
          command.fileKey(),
          isMultipartETag,
          documentSizeMb);
      return documentStorage.calculateSha256(command.bucketName(), command.fileKey());
    }

    return command.eTag();
  }

  private DocumentOutbox createDocumentOutbox(
      DocumentUploadCommand command, Document document, boolean isUpdated) {
    return DocumentOutbox.builder()
        .aggregateType(AggregateType.DOCUMENT)
        .aggregateId(document.getId().toString())
        .eventType(EventType.isNewOrUpdated(isUpdated))
        .payload(
            DocumentOutboxPayload.of(
                command.fileKey(), command.bucketName(), command.contentType()))
        .build();
  }

  private void persistAndDeleteOutboxEvent(DocumentOutbox outboxEvent) {
    documentOutboxRepository.saveAndFlush(outboxEvent);
    documentOutboxRepository.delete(outboxEvent); // only for CDC to catch, prevent WAL bloats
  }
}
