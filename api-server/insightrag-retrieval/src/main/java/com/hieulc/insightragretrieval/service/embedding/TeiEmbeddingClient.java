package com.hieulc.insightragretrieval.service.embedding;

import com.hieulc.insightragretrieval.dto.EmbeddingRequest;
import com.hieulc.insightragretrieval.dto.TeiErrorResponse;
import com.hieulc.insightragretrieval.exception.infras.TeiClientException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeiEmbeddingClient implements EmbeddingModel {

  private final RestClient teiRestClient;
  private final ObjectMapper objectMapper;

  @Override
  public Response<Embedding> embed(String text) {
    EmbeddingRequest request = EmbeddingRequest.forText(text, true);
    float[][] embeddingResult = getEmbeddings(request);
    return Response.from(Embedding.from(embeddingResult[0]));
  }

  public float[][] getEmbeddings(EmbeddingRequest request) {
    return teiRestClient
        .post()
        .uri("/embed")
        .body(request)
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            (req, response) -> {
              TeiErrorResponse errorBody = parseError(response.getBody());
              log.error(
                  "TEI Client error: {} - {} (Status: {})",
                  errorBody.error(),
                  errorBody.errorType(),
                  response.getStatusCode().value());
              throw new TeiClientException(
                  errorBody.error(), errorBody.errorType(), response.getStatusCode().value());
            })
        .body(float[][].class);
  }

  private TeiErrorResponse parseError(InputStream input) {
    try {
      return objectMapper.readValue(input, TeiErrorResponse.class);
    } catch (JacksonException e) {
      log.warn("Could not parse TEI error response body", e);
      throw new TeiClientException(
          "Unknown error occurred", "Unknown", HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
  }
}
