package com.hieulc.insightragworker.document.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.hieulc.insightragworker.config.properties.DocumentValidationProperties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentValidationServiceTest {

  @Mock private DocumentValidationProperties properties;

  @InjectMocks private DocumentValidationService documentValidationService;

  @BeforeEach
  void setUp() {
    lenient().when(properties.allowedContentTypes()).thenReturn(Set.of("application/pdf"));
    lenient().when(properties.maxFileSizeMb()).thenReturn(10.0);
  }

  @ParameterizedTest(name = "Size {0} should evaluate to {1}")
  @CsvSource({
    "5.0, true",
    "10.0, true",
    "0.1, true",
    "10.1, false",
    "0.0, false",
    "-5.0, false",
  })
  void isSizeValid_validates_size_boundaries(double sizeInMb, boolean expectedResult) {
    assertThat(documentValidationService.isSizeValid(sizeInMb)).isEqualTo(expectedResult);
  }

  @ParameterizedTest(name = "Accept valid content type: ''{0}''")
  @ValueSource(strings = {"application/pdf", "APPLICATION/PDF", " application/pdf "})
  void isSupportedFormat_returns_true_for_supported_formats(String contentType) {
    assertThat(documentValidationService.isSupportedFormat(contentType)).isTrue();
  }

  @ParameterizedTest(name = "Reject invalid content type: ''{0}''")
  @ValueSource(
      strings = {
        "text/plain",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/csv",
        "image/png"
      })
  void isSupportedFormat_returns_false_for_unsupported_formats(String format) {
    assertThat(documentValidationService.isSupportedFormat(format)).isFalse();
  }

  @Test
  void isValidDocument_returns_false_when_content_type_is_null_or_blank() {
    assertThat(documentValidationService.isValidDocument(null, 5.0)).isFalse();
    assertThat(documentValidationService.isValidDocument("", 5.0)).isFalse();
    assertThat(documentValidationService.isValidDocument("   ", 5.0)).isFalse();
  }

  @Test
  void isValidDocument_returns_true_when_both_size_and_format_is_valid() {
    assertThat(documentValidationService.isValidDocument("application/pdf", 5.0)).isTrue();
    assertThat(documentValidationService.isValidDocument("text/plain", 5.0)).isFalse();
    assertThat(documentValidationService.isValidDocument("application/pdf", 15.0)).isFalse();
  }
}
