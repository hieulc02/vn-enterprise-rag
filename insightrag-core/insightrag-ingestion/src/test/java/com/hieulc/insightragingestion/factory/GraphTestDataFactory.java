package com.hieulc.insightragingestion.factory;

import com.hieulc.insightragingestion.dto.graph.DocumentChunk;
import com.hieulc.insightragingestion.dto.graph.Entity;
import com.hieulc.insightragingestion.dto.graph.Relationship;
import java.util.*;
import java.util.stream.IntStream;

public class GraphTestDataFactory {

  public static DocumentChunk createDefaultChunk(String chunkId, String docKey) {
    return new DocumentChunk(
        chunkId,
        "Default-Text",
        new DocumentChunk.ChunkMetadata(docKey, 1),
        1,
        new float[] {0.1f, 0.2f});
  }

  public static DocumentChunk createChunk(String chunkId, String docKey, String text, int index) {
    DocumentChunk.ChunkMetadata metadata = null;
    if (docKey != null && index > 0) {
      metadata = new DocumentChunk.ChunkMetadata(docKey, index);
    }
    return new DocumentChunk(chunkId, text, metadata, index, new float[] {0.1f, 0.2f});
  }

  public static List<DocumentChunk> generateListChunks(String docKey, int expectedChunksNum) {
    List<DocumentChunk> chunks = new ArrayList<>();
    for (int i = 1; i <= expectedChunksNum; i++) {
      String chunkId = String.format("c%d", i);
      String text = String.format("TEXT-%d", i);
      chunks.add(createChunk(chunkId, docKey, text, i));
    }

    return chunks;
  }

  public static List<Entity> generateListEntities(int expectedEntitiesNum) {
    return IntStream.range(0, expectedEntitiesNum)
        .mapToObj(i -> createEntity("e" + (i + 1), null, null))
        .toList();
  }

  public static List<Relationship> generateListRelationships(int expectedRelationshipsNum) {
    return IntStream.range(0, expectedRelationshipsNum)
        .mapToObj(i -> createRelationship("src", "tgt", "RELATED_TO", null, 1.0))
        .toList();
  }

  public static Entity createEntity(
      String entityId, List<String> aliases, Set<String> sourceChunkIds) {

    Set<String> resolvedSourceChunkIds =
        (sourceChunkIds == null) ? Set.of() : Set.copyOf(sourceChunkIds);

    Map<String, Object> properties = new HashMap<>();
    properties.put("attribute", "fake");
    properties.put("embedding", List.of(0.1f, 0.2f));
    properties.put("null-value", null);
    properties.put("empty-list", List.of());

    return new Entity(
        entityId,
        "Default-Title",
        aliases,
        List.of("Node", "Aliases"),
        "Entity-Description",
        resolvedSourceChunkIds,
        properties);
  }

  public static Relationship createRelationship(
      String source, String target, String type, Set<String> sourceChunkIds, double weight) {
    String id = UUID.randomUUID().toString();

    Set<String> resolvedSourceChunkIds =
        (sourceChunkIds == null) ? Set.of() : Set.copyOf(sourceChunkIds);

    double resolvedWeight = (weight != 0.0) ? weight : 1.0;

    Map<String, Object> properties = new HashMap<>();
    properties.put("props", "value");
    properties.put("embedding", List.of(0.1f, 0.2f));
    properties.put("null-value", null);
    properties.put("empty-list", List.of());

    return new Relationship(
        id,
        source,
        target,
        type,
        "Default-Description",
        resolvedWeight,
        resolvedSourceChunkIds,
        properties);
  }
}
