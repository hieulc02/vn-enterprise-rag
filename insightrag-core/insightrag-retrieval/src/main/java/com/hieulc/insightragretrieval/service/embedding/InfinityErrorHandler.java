package com.hieulc.insightragretrieval.service.embedding;

import com.hieulc.insightragretrieval.dto.embedding.EmbeddingErrorResponse;
import com.hieulc.insightragretrieval.exception.infras.EmbeddingClientException;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class InfinityErrorHandler {

  private final ObjectMapper objectMapper;

  public EmbeddingClientException handle(InputStream input, int statusCode) {
    try (InputStream is = input) {
      EmbeddingErrorResponse response = objectMapper.readValue(is, EmbeddingErrorResponse.class);

      if (response != null && response.detail() != null && !response.detail().isEmpty()) {
        EmbeddingErrorResponse.ValidationError validationError = response.detail().getFirst();
        String path = formatLocationPath(validationError.loc());
        String formatedError =
            String.format("Validation error at '%s': %s", path, validationError.msg());

        return new EmbeddingClientException(formatedError, validationError.type(), statusCode);
      }

    } catch (JacksonException e) {
      log.warn("Could not parse Infinity error response body. HTTP Status: {}", statusCode, e);
      throw new EmbeddingClientException(
          "Unknown error occurred, failed to parse response", "PARSE_ERROR", statusCode);
    } catch (Exception e) {
      log.error("Unexpected error while handling Infinity client exception", e);
      return new EmbeddingClientException("Internal client error", "INTERNAL_ERROR", statusCode);
    }

    return new EmbeddingClientException(
        "Unknown Infinity client error", "UNKNOWN_ERROR", statusCode);
  }

  private String formatLocationPath(List<Object> loc) {
    if (loc == null || loc.isEmpty()) return "root";

    StringBuilder path = new StringBuilder();
    for (Object node : loc) {
      if (node instanceof Integer) {
        path.append("[").append(node).append("]");
      } else if (node instanceof String) {
        if (!path.isEmpty()) {
          path.append(".");
        }
        path.append(node);
      }
    }

    return path.toString();
  }
}
