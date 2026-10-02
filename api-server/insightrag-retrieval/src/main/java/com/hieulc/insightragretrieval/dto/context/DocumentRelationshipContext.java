package com.hieulc.insightragretrieval.dto.context;


public record DocumentRelationshipContext(
    NodeEdge nodeEdge, double sematicScore, DocumentNode sourceNode, DocumentNode targetNode) {}
