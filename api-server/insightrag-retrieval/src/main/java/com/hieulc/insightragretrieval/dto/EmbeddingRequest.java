package com.hieulc.insightragretrieval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbeddingRequest(
    Object inputs,
    Integer dimensions,
    Boolean normalize,
    @JsonProperty("prompt_name") String promptName,
    Boolean truncate,
    @JsonProperty("truncation_direction") String truncationDirection) {

  public static EmbeddingRequest forText(String text, Boolean normalize) {
    return new EmbeddingRequest(text, null, normalize, null, null, null);
  }
}
