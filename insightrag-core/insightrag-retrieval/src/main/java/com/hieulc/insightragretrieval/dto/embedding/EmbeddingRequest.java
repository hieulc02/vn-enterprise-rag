package com.hieulc.insightragretrieval.dto.embedding;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbeddingRequest(
    String model,
    @JsonProperty("encoding_format") String encodingFormat,
    String user,
    Integer dimensions,
    List<String> input,
    String modality) {

  public static EmbeddingRequest forText(String text, String model) {
    return new EmbeddingRequest(model, "float", null, null, List.of(text), "text");
  }
}
