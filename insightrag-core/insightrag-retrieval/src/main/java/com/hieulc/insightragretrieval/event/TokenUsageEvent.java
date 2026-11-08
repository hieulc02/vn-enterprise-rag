package com.hieulc.insightragretrieval.event;

import dev.langchain4j.model.output.TokenUsage;

public record TokenUsageEvent(String userId, String sessionId, TokenUsage tokenUsage) {}
