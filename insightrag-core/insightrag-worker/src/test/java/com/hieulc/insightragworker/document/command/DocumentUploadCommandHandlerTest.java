package com.hieulc.insightragworker.document.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.hieulc.insightragworker.config.properties.DocumentValidationProperties;
import com.hieulc.insightragworker.document.enums.DocumentStatus;
import com.hieulc.insightragworker.document.model.Document;
import com.hieulc.insightragworker.document.port.DocumentCustomRepository;
import com.hieulc.insightragworker.document.port.DocumentStorage;
import com.hieulc.insightragworker.document.validation.DocumentValidationService;
import com.hieulc.insightragworker.outbox.enums.AggregateType;
import com.hieulc.insightragworker.outbox.enums.EventType;
import com.hieulc.insightragworker.outbox.model.DocumentOutbox;
import com.hieulc.insightragworker.outbox.port.DocumentOutboxRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DocumentUploadCommandHandlerTest {

  @Mock private DocumentCustomRepository documentRepository;
  @Mock private DocumentOutboxRepository documentOutboxRepository;

  @Mock private DocumentStorage documentStorage;
  @Mock private DocumentValidationService documentValidationService;
  @Mock private DocumentValidationProperties properties;

  @InjectMocks private DocumentUploadCommandHandler handler;

  @Captor private ArgumentCaptor<Document> documentCaptor;
  @Captor private ArgumentCaptor<DocumentOutbox> outboxCaptor;

  private final String FILE_KEY = "bucket/document.pdf";
  private final String PDF_CONTENT_TYPE = "application/pdf";
  private final long DOCUMENT_SIZE_15MB = 15 * 1024 * 1024;

  @BeforeEach
  void setUp() {
    lenient()
        .when(documentRepository.findFileKeyByHash(anyString(), anyString()))
        .thenReturn(Optional.empty());
  }

  @Test
  void handle_throws_exception_when_document_validation_fails() {
    var command =
        createCommand(
            "bucket/document.docx",
            2 * 1024 * 1024,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    when(documentValidationService.isValidDocument(anyString(), anyDouble())).thenReturn(false);
    when(properties.allowedContentTypes()).thenReturn(Set.of(PDF_CONTENT_TYPE));
    when(properties.maxFileSizeMb()).thenReturn(10.0);

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid document file [bucket/document.docx] type [application/vnd.openxmlformats-officedocument.wordprocessingml.document] or size [2.00 MB]. Supported Document Types: [application/pdf] and Size Limit: [10.00 MB]");

    verifyNoInteractions(documentRepository, documentOutboxRepository, documentStorage);
  }

  @Test
  void handle_calculates_sha256_and_creates_outbox_when_large_existing_file_updated() {
    var command = createCommand(FILE_KEY, DOCUMENT_SIZE_15MB, PDF_CONTENT_TYPE);

    when(documentValidationService.isValidDocument(anyString(), anyDouble())).thenReturn(true);
    when(properties.eTagThresholdMb()).thenReturn(14.0);
    when(documentStorage.calculateSha256(anyString(), anyString()))
        .thenReturn("calculated-sha256-hash");
    when(documentRepository.upsertWithSequencerCheck(any(Document.class)))
        .thenReturn(Optional.of(false));

    handler.handle(command);

    verify(documentStorage).calculateSha256("bucket", "bucket/document.pdf");
    verify(documentRepository).upsertWithSequencerCheck(documentCaptor.capture());
    assertThat(documentCaptor.getValue().getHashContent()).isEqualTo("calculated-sha256-hash");

    verify(documentOutboxRepository).saveAndFlush(outboxCaptor.capture());
    assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(EventType.UPDATED);
  }

  @Test
  void handle_uses_etag_and_creates_outbox_when_new_small_file_uploaded() {
    var command = createCommand(FILE_KEY, DOCUMENT_SIZE_15MB, PDF_CONTENT_TYPE);

    when(documentValidationService.isValidDocument(anyString(), anyDouble())).thenReturn(true);
    when(properties.eTagThresholdMb()).thenReturn(16.0);

    when(documentRepository.upsertWithSequencerCheck(any(Document.class)))
        .thenReturn(Optional.of(true));

    handler.handle(command);

    verify(documentRepository).upsertWithSequencerCheck(documentCaptor.capture());
    Document savedDocument = documentCaptor.getValue();
    assertThat(savedDocument.getHashContent()).isEqualTo("1b2cf535f27731c974343645a3985328");
    assertThat(savedDocument.getFileKey()).isEqualTo(FILE_KEY);
    assertThat(savedDocument.getStatus()).isEqualTo(DocumentStatus.ACTIVE);

    verify(documentOutboxRepository).saveAndFlush(outboxCaptor.capture());
    DocumentOutbox outbox = outboxCaptor.getValue();
    assertThat(outbox.getAggregateType()).isEqualTo(AggregateType.DOCUMENT);
    assertThat(outbox.getEventType()).isEqualTo(EventType.NEW);
  }

  @Test
  void handle_returns_early_if_sequencer_is_stale() {
    var command = createCommand(FILE_KEY, DOCUMENT_SIZE_15MB, PDF_CONTENT_TYPE);

    when(documentValidationService.isValidDocument(anyString(), anyDouble())).thenReturn(true);
    when(properties.eTagThresholdMb()).thenReturn(16.0);
    when(documentRepository.upsertWithSequencerCheck(any(Document.class)))
        .thenReturn(Optional.empty());

    handler.handle(command);

    verify(documentRepository).upsertWithSequencerCheck(any(Document.class));
    verifyNoInteractions(documentOutboxRepository);
  }

  @Test
  void handle_returns_early_if_content_is_duplicate() {
    var command = createCommand("bucket/document_v1.pdf", DOCUMENT_SIZE_15MB, PDF_CONTENT_TYPE);

    when(documentValidationService.isValidDocument(anyString(), anyDouble())).thenReturn(true);
    when(properties.eTagThresholdMb()).thenReturn(14.0);

    String expectedHash = "calculated-sha-256";
    when(documentStorage.calculateSha256(command.bucketName(), command.fileKey()))
        .thenReturn(expectedHash);

    when(documentRepository.findFileKeyByHash(expectedHash, command.fileKey()))
        .thenReturn(Optional.of(FILE_KEY));

    handler.handle(command);

    verifyNoInteractions(documentOutboxRepository);
  }

  @Test
  void handle_catches_and_suppress_DataIntegrityViolation_on_race_condition() {
    var command = createCommand(FILE_KEY, DOCUMENT_SIZE_15MB, PDF_CONTENT_TYPE);
    when(documentValidationService.isValidDocument(anyString(), anyDouble())).thenReturn(true);
    when(properties.eTagThresholdMb()).thenReturn(16.0);
    when(documentRepository.findFileKeyByHash(any(), any())).thenReturn(Optional.empty());
    when(documentRepository.upsertWithSequencerCheck(any()))
        .thenThrow(
            new DataIntegrityViolationException("duplicate key value violates unique constraint"));

    assertDoesNotThrow(() -> handler.handle(command));

    verify(documentOutboxRepository, never()).saveAndFlush(any());
  }

  private DocumentUploadCommand createCommand(String fileKey, long sizeBytes, String contentType) {
    return new DocumentUploadCommand(
        fileKey,
        "0055AED6DCD891EFE2",
        "1b2cf535f27731c974343645a3985328",
        "bucket",
        contentType,
        sizeBytes);
  }
}
