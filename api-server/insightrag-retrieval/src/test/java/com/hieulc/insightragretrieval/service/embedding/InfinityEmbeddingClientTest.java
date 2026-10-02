package com.hieulc.insightragretrieval.service.embedding;

import static com.hieulc.insightragretrieval.config.properties.EmbeddingPropertiesFixtures.defaultEmbeddingProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hieulc.insightragretrieval.config.EmbeddingTestConfig;
import com.hieulc.insightragretrieval.config.properties.EmbeddingProperties;
import com.hieulc.insightragretrieval.exception.infras.EmbeddingClientException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(InfinityEmbeddingClient.class)
@Import(EmbeddingTestConfig.class)
class InfinityEmbeddingClientTest {

  @MockitoBean private InfinityErrorHandler errorHandler;
  @MockitoBean private EmbeddingProperties embeddingProperties;
  @Autowired private InfinityEmbeddingClient client;
  @Autowired private MockRestServiceServer mockServer;

  @Test
  void embed_success() {
    given(embeddingProperties.model()).willReturn(defaultEmbeddingProperties().model());

    String mockJsonResponse =
        """
        {
          "data": [
            { "index": 0, "embedding": [0.1, -0.2, 0.3] }
          ]
        }
        """;

    mockServer
        .expect(requestTo("/embeddings"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(mockJsonResponse, MediaType.APPLICATION_JSON));

    var result = client.embed("success");
    assertThat(result.content().vector()).containsExactly(0.1f, -0.2f, 0.3f);
  }

  @Test
  void embed_throw_exceptions_when_return_empty_list() {
    given(embeddingProperties.model()).willReturn(defaultEmbeddingProperties().model());

    String mockJsonResponse =
        """
        {
          "data": []
        }
        """;

    mockServer
        .expect(requestTo("/embeddings"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(mockJsonResponse, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.embed("empty"))
        .isInstanceOf(EmbeddingClientException.class)
        .hasMessageContaining("Empty embedding returned")
        .hasFieldOrPropertyWithValue("errorType", "EMPTY_DATA")
        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_GATEWAY.value());
  }

  @Test
  void embed_throw_exceptions_when_internal_server_error() {
    given(embeddingProperties.model()).willReturn(defaultEmbeddingProperties().model());

    given(errorHandler.handle(any(), eq(500)))
        .willReturn(new EmbeddingClientException("Handler Server Error", "SERVER_ERROR", 500));

    mockServer
        .expect(requestTo("/embeddings"))
        .andRespond(withServerError().body("Internal Server Failure"));

    assertThatThrownBy(() -> client.embed("error"))
        .isInstanceOf(EmbeddingClientException.class)
        .hasMessageContaining("Handler Server Error")
        .hasFieldOrPropertyWithValue("errorType", "SERVER_ERROR");
  }
}
