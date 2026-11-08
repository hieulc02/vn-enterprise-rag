package com.hieulc.insightragworker.document.command;

import com.hieulc.insightragworker.common.command.CommandHandler;
import com.hieulc.insightragworker.document.model.DocumentDeleteResult;
import com.hieulc.insightragworker.document.port.DocumentCustomRepository;
import com.hieulc.insightragworker.outbox.enums.AggregateType;
import com.hieulc.insightragworker.outbox.enums.EventType;
import com.hieulc.insightragworker.outbox.model.DocumentOutbox;
import com.hieulc.insightragworker.outbox.model.DocumentOutboxPayload;
import com.hieulc.insightragworker.outbox.port.DocumentOutboxRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
class DocumentDeleteCommandHandler implements CommandHandler<DocumentDeleteCommand> {

  private final DocumentCustomRepository documentCustomRepository;
  private final DocumentOutboxRepository documentOutboxRepository;

  @Override
  @Transactional
  public void handle(DocumentDeleteCommand command) {

    Optional<DocumentDeleteResult> deleteResult =
        documentCustomRepository.updateSoftDeleteDocument(command.fileKey(), command.sequenceId());

    if (deleteResult.isEmpty()) {
      log.info("Document {} is already stale or has been deleted", command.fileKey());
      return;
    }

    DocumentOutbox outboxEvent =
        createDocumentDeletedOutboxEvent(command, deleteResult.get().id().toString());
    persistAndDeleteOutboxEvent(outboxEvent);
  }

  private DocumentOutbox createDocumentDeletedOutboxEvent(
      DocumentDeleteCommand command, String deletedId) {
    return DocumentOutbox.builder()
        .aggregateType(AggregateType.DOCUMENT)
        .aggregateId(deletedId)
        .eventType(EventType.DELETED)
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
