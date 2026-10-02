package com.hieulc.insightragretrieval.dto.context;

import java.util.List;
import java.util.Map;

public record DocumentNode(
    String id, Object title, List<String> labels, Map<String, Object> properties) {}
