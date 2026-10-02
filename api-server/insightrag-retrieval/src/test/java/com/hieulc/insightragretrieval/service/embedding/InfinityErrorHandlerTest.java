package com.hieulc.insightragretrieval.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.hieulc.insightragretrieval.exception.infras.EmbeddingClientException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InfinityErrorHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final InfinityErrorHandler errorHandler = new InfinityErrorHandler(objectMapper);

  @Test
  void handle_parse_fast_api_validation_error_and_format_path() {
    // arrange
    String jsonResponse =
        """
    {
      "detail": [
        {
          "loc": ["body", "input", 0],
          "msg": "field required",
          "type": "value_error.missing"
        }
      ]
    }
    """;

    InputStream stream = new ByteArrayInputStream(jsonResponse.getBytes());

    // act
    EmbeddingClientException embeddingClientException = errorHandler.handle(stream, 422);

    // assert
    assertThat(embeddingClientException.getStatusCode()).isEqualTo(422);
    assertThat(embeddingClientException.getMessage())
        .isEqualTo("Validation error at 'body.input[0]': field required");
    assertThat(embeddingClientException.getErrorType()).isEqualTo("value_error.missing");
  }
}
