package com.hieulc.insightragworker.document.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.hieulc.insightragworker.document.model.DocumentDeleteResult;
import com.hieulc.insightragworker.document.port.DocumentCustomRepository;
import com.hieulc.insightragworker.outbox.enums.AggregateType;
import com.hieulc.insightragworker.outbox.enums.EventType;
import com.hieulc.insightragworker.outbox.model.DocumentOutbox;
import com.hieulc.insightragworker.outbox.port.DocumentOutboxRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentDeleteCommandHandlerTest {

  @Mock private DocumentCustomRepository documentRepository;

  @Mock private DocumentOutboxRepository documentOutboxRepository;

  @InjectMocks private DocumentDeleteCommandHandler documentDeleteCommandHandler;

  private final String fileKey = "deleted-document.pdf";

  @Test
  void handle_returns_early_if_sequencer_is_stale() {
    var deleteCommand = createCommand();
    when(documentRepository.updateSoftDeleteDocument(anyString(), anyString()))
        .thenReturn(Optional.empty());

    documentDeleteCommandHandler.handle(deleteCommand);

    verify(documentRepository).updateSoftDeleteDocument(eq(fileKey), eq("sequence-id"));
    verifyNoInteractions(documentOutboxRepository);
  }

  @Test
  void handle_updates_document_status_and_creates_outbox_event() {
    DocumentDeleteCommand deleteCommand = createCommand();

    UUID mockDbId = UUID.randomUUID();
    when(documentRepository.updateSoftDeleteDocument(fileKey, "sequence-id"))
        .thenReturn(Optional.of(new DocumentDeleteResult(mockDbId)));

    documentDeleteCommandHandler.handle(deleteCommand);

    ArgumentCaptor<DocumentOutbox> captor = ArgumentCaptor.forClass(DocumentOutbox.class);
    verify(documentOutboxRepository).saveAndFlush(captor.capture());

    DocumentOutbox capturedOutbox = captor.getValue();
    verify(documentOutboxRepository).delete(capturedOutbox);
    assertThat(capturedOutbox.getAggregateId()).isEqualTo(mockDbId.toString());
    assertThat(capturedOutbox.getAggregateType()).isEqualTo(AggregateType.DOCUMENT);
    assertThat(capturedOutbox.getEventType()).isEqualTo(EventType.DELETED);
  }

  private DocumentDeleteCommand createCommand() {
    return new DocumentDeleteCommand(fileKey, "sequence-id", "bucket", "application/pdf");
  }
}
