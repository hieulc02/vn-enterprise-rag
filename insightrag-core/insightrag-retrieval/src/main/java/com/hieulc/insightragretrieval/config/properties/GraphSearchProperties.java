package com.hieulc.insightragretrieval.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "insightrag.neo4j.search.tuning")
public record GraphSearchProperties(
    int rrfConstant,
    double weightFullText,
    double weightVector,
    double minVectorScore,
    double edgeSemanticGuardrail) {}
