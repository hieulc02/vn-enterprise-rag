package com.hieulc.insightragingestion.factory;

import com.hieulc.insightragingestion.dto.DocumentChunk;
import com.hieulc.insightragingestion.dto.Entity;
import com.hieulc.insightragingestion.dto.Relationship;
import java.util.*;
import java.util.stream.IntStream;

public class GraphTestDataFactory {

  public static DocumentChunk createDefaultChunk(String chunkId, String docId) {
    return new DocumentChunk(
        chunkId,
        "Default-Text",
        new DocumentChunk.ChunkMetadata(docId, 1),
        1,
        new float[] {0.1f, 0.2f});
  }

  public static DocumentChunk createChunk(String chunkId, String docId, String text, int index) {
    return new DocumentChunk(
        chunkId,
        text,
        new DocumentChunk.ChunkMetadata(docId, index),
        index,
        new float[] {0.1f, 0.2f});
  }

  public static List<DocumentChunk> generateListChunks(String docId, int expectedChunksNum) {
    List<DocumentChunk> chunks = new ArrayList<>();
    for (int i = 1; i <= expectedChunksNum; i++) {
      String chunkId = String.format("c%d", i);
      String text = String.format("TEXT-%d", i);
      chunks.add(createChunk(chunkId, docId, text, i));
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
    List<String> resolvedAliases =
        (aliases == null || aliases.isEmpty())
            ? List.of("Alias 1", "Alias 2")
            : List.copyOf(aliases);

    Set<String> resolvedSourceChunkIds =
        (sourceChunkIds == null) ? Set.of() : Set.copyOf(sourceChunkIds);

    return new Entity(
        entityId,
        "Default-Title",
        resolvedAliases,
        List.of("Entity", "Aliases"),
        "Entity-Description",
        resolvedSourceChunkIds,
        Map.of("attribute", "fake", "embedding", List.of(0.1f, 0.2f)));
  }

  public static Relationship createRelationship(
      String source, String target, String type, Set<String> sourceChunkIds, double weight) {
    String id = UUID.randomUUID().toString();

    Set<String> resolvedSourceChunkIds =
        (sourceChunkIds == null) ? Set.of() : Set.copyOf(sourceChunkIds);

    double resolvedWeight = (weight != 0.0) ? weight : 1.0;

    return new Relationship(
        id,
        source,
        target,
        type,
        "Default-Description",
        resolvedWeight,
        resolvedSourceChunkIds,
        Map.of("embedding", List.of(0.1f, 0.2f), "props", "value"));
  }
}
