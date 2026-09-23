package com.hieulc.insightragretrieval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TeiErrorResponse(String error, @JsonProperty("error_type") String errorType) {}
