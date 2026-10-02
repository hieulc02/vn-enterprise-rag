package com.hieulc.insightragretrieval.service.embedding;

import com.hieulc.insightragretrieval.config.properties.EmbeddingProperties;
import com.hieulc.insightragretrieval.dto.embedding.EmbeddingRequest;
import com.hieulc.insightragretrieval.dto.embedding.EmbeddingResponse;
import com.hieulc.insightragretrieval.exception.infras.EmbeddingClientException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class InfinityEmbeddingClient implements EmbeddingModel {

  private final InfinityErrorHandler errorHandler;
  private final RestClient infinityRestClient;
  private final EmbeddingProperties embeddingProperties;

  public InfinityEmbeddingClient(
      @Qualifier("infinityRestClient") RestClient infinityRestClient,
      InfinityErrorHandler errorHandler,
      EmbeddingProperties embeddingProperties) {
    this.infinityRestClient = infinityRestClient;
    this.errorHandler = errorHandler;
    this.embeddingProperties = embeddingProperties;
  }

  @Override
  @Retryable(EmbeddingClientException.class)
  public Response<Embedding> embed(String text) {
    EmbeddingRequest request = EmbeddingRequest.forText(text, embeddingProperties.model());
    List<float[]> embeddings = getEmbeddings(request);

    if (embeddings.isEmpty()) {
      throw new EmbeddingClientException(
          "Empty embedding returned", "EMPTY_DATA", HttpStatus.BAD_GATEWAY.value());
    }
    return Response.from(Embedding.from(embeddings.getFirst()));
  }

  private List<float[]> getEmbeddings(EmbeddingRequest request) {
    EmbeddingResponse response =
        infinityRestClient
            .post()
            .uri("/embeddings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (req, resp) -> {
                  throw errorHandler.handle(resp.getBody(), resp.getStatusCode().value());
                })
            .body(EmbeddingResponse.class);

    if (response != null && response.data() != null) {
      return response.data().stream().map(EmbeddingResponse.EmbeddingData::embedding).toList();
    }

    throw new EmbeddingClientException(
        "Failed to generate embedding from Infinity",
        "UNKNOWN_ERROR",
        HttpStatus.BAD_GATEWAY.value());
  }
}
