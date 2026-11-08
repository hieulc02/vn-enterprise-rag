package com.hieulc.insightragingestion.repository.graph;

import com.hieulc.insightragingestion.dto.graph.DocumentChunk;
import com.hieulc.insightragingestion.dto.graph.Entity;
import com.hieulc.insightragingestion.dto.graph.Relationship;
import java.util.*;

final class GraphMapper {
  private GraphMapper() {}

  static Map<String, Object> toChunkMap(DocumentChunk c) {
    Map<String, Object> map = new HashMap<>();
    map.put("chunk_id", c.chunkId());
    map.put("text", c.text());
    map.put("chunk_index", c.chunkIndex());
    map.put("embedding", c.embedding());

    Map<String, Object> metadata = new HashMap<>();
    if (c.metadata() != null) {
      metadata.put("document_key", c.metadata().documentKey());
      metadata.put("page_number", c.metadata().pageNumber());
    }
    map.put("metadata", metadata);
    return map;
  }

  static Map<String, Object> toEntityNodeMap(Entity e) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", e.id());
    map.put("title", e.title());
    map.put("description", e.description());
    map.put("labels", e.labels());
    map.put("aliases", e.aliases());
    map.put("properties", sanitizeProperties(e.properties()));

    return map;
  }

  static Map<String, Object> toEntityEdgeMap(Entity e) {
    return Map.of(
        "id", e.id(),
        "chunk_ids", e.sourceChunkIds());
  }

  static Map<String, Object> toRelationshipMap(Relationship rel) {

    Map<String, Object> batchMap = new HashMap<>();
    batchMap.put("source", rel.source());
    batchMap.put("target", rel.target());

    Map<String, Object> props = new HashMap<>();
    if (rel.properties() != null) {
      props.putAll(sanitizeProperties(rel.properties()));
    }
    if (rel.description() != null && !rel.description().isBlank()) {
      props.put("description", rel.description());
    }
    props.put("weight", rel.weight());

    if (rel.sourceChunkIds() != null && !rel.sourceChunkIds().isEmpty()) {
      props.put("source_chunk_ids", rel.sourceChunkIds());
    }
    batchMap.put("props", props);
    return batchMap;
  }

  static Map<String, Object> sanitizeProperties(Map<String, Object> properties) {
    if (properties == null || properties.isEmpty()) return Collections.emptyMap();

    Map<String, Object> sanitized = new HashMap<>();
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      switch (value) {
        case null -> {
          continue;
        }
        case Collection<?> collection when collection.isEmpty() -> {
          continue;
        }
        case List<?> list when key.equals("embedding") -> {
          float[] vector = new float[list.size()];
          for (int i = 0; i < list.size(); i++) {
            vector[i] = ((Number) list.get(i)).floatValue();
          }
          sanitized.put(key, vector);
          continue;
        }
        default -> {}
      }

      sanitized.put(key, value);
    }
    return sanitized;
  }
}
