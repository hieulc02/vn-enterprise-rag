package com.hieulc.insightragingestion.repository;

import com.hieulc.insightragingestion.dto.DocumentChunk;
import com.hieulc.insightragingestion.dto.Entity;
import com.hieulc.insightragingestion.dto.Relationship;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.Statement;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class GraphRepositoryImpl implements GraphCustomRepository {

  private final Neo4jClient neo4jClient;

  @Override
  @Transactional
  public void batchInsertChunks(String documentId, List<DocumentChunk> chunks) {

    String chunkCypher = buildMergeChunksNodeAndEdgeCypher();

    List<Map<String, Object>> batch =
        chunks.stream()
            .map(
                c -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("chunk_id", c.chunkId());
                  map.put("text", c.text());
                  map.put("chunk_index", c.chunkIndex());
                  map.put("embedding", c.embedding());

                  Map<String, Object> metadata = new HashMap<>();
                  if (c.metadata() != null) {
                    metadata.put("document_id", c.metadata().documentId());
                    metadata.put("page_number", c.metadata().pageNumber());
                  }
                  map.put("metadata", metadata);
                  return map;
                })
            .toList();

    neo4jClient.query(chunkCypher).bind(batch).to("batch").bind(documentId).to("docId").run();
  }

  @Override
  @Transactional
  public void batchInsertEntities(List<Entity> entities) {

    String nodeCypher = buildMergeEntityCypher();

    List<Map<String, Object>> nodeBatch =
        entities.stream()
            .map(
                e -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("id", e.id());
                  map.put("title", e.title());
                  map.put("description", e.description());
                  map.put("labels", e.labels());
                  map.put("aliases", e.aliases());
                  map.put("properties", sanitizeProperties(e.properties()));

                  return map;
                })
            .toList();

    neo4jClient.query(nodeCypher).bind(nodeBatch).to("batch").run();

    String edgeCypher = buildMergeEntityEdgeCypher();

    List<Map<String, Object>> edgeBatch =
        entities.stream()
            .filter(e -> e.sourceChunkIds() != null && !e.sourceChunkIds().isEmpty())
            .map(
                e ->
                    Map.of(
                        "id", e.id(),
                        "chunk_ids", e.sourceChunkIds()))
            .toList();

    if (!edgeBatch.isEmpty()) {
      neo4jClient.query(edgeCypher).bind(edgeBatch).to("batch").run();
    }
  }

  @Override
  @Transactional
  public void batchInsertRelationship(List<Relationship> relationships) {
    Map<String, List<Map<String, Object>>> groupedRelationship = new HashMap<>();

    relationships.forEach(
        rel -> {
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

          Map<String, Object> batchMap = new HashMap<>();
          batchMap.put("source", rel.source());
          batchMap.put("target", rel.target());
          batchMap.put("props", props);

          groupedRelationship.computeIfAbsent(rel.type(), k -> new ArrayList<>()).add(batchMap);
        });

    for (Map.Entry<String, List<Map<String, Object>>> entry : groupedRelationship.entrySet()) {
      String type = entry.getKey();
      List<Map<String, Object>> batch = entry.getValue();

      String relationshipCypher = buildMergeRelationshipCypher(type);

      neo4jClient.query(relationshipCypher).bind(batch).to("batch").run();
    }
  }

  @Override
  @Transactional
  public int linkChunks(String documentId) {
    String linkCypher = buildLinkChunksCypher();

    return neo4jClient
        .query(linkCypher)
        .bind(documentId)
        .to("docId")
        .fetchAs(Integer.class)
        .all()
        .stream()
        .findFirst()
        .orElse(0);
  }

  String buildMergeChunksNodeAndEdgeCypher() {
    var docIdParam = Cypher.parameter("docId");
    var batchParam = Cypher.parameter("batch");
    var item = Cypher.name("item");
    Node d = Cypher.node("Document").named("d");
    Node c = Cypher.node("DocumentChunk").named("c");

    Statement chunkStatement =
        Cypher.merge(d.withProperties("document_id", docIdParam))
            .with(d)
            .unwind(batchParam)
            .as(item)
            .merge(c.withProperties("chunk_id", item.property("chunk_id")))
            .merge(c.relationshipTo(d, "PART_OF"))
            .set(
                c.property("text").to(item.property("text")),
                c.property("chunk_index").to(item.property("chunk_index")),
                c.property("embedding").to(item.property("embedding")))
            .mutate(c, item.property("metadata"))
            .build();

    return chunkStatement.getCypher();
  }

  String buildMergeEntityCypher() {
    // Drop because Cypher-DLS requires property assignments
    // to use an even number of arguments
    //    var batchParam = Cypher.parameter("batch");
    //    var item = Cypher.name("item");
    //    Node e = Cypher.node("Entity").named("e");
    //    Statement nodeStatement =
    //        Cypher.unwind(batchParam)
    //            .as(item)
    //            .merge(e.withProperties("id", item.property("id")))
    //            .set(
    //                e.property("title").to(item.property("title")),
    //                e.property("description").to(item.property("description")))
    //            .set(Cypher.raw("e:$(item.labels)"))
    //            .set(
    //                e.property("aliases")
    //                    .to(Cypher.coalesce(item.property("aliases"), Cypher.listOf())))
    //            .mutate(e, item.property("properties"))
    //            .build();

    return """
      UNWIND $batch AS item
      MERGE (e:Entity {id: item.id})
      SET e.title = item.title, e.description = item.description
      SET e.aliases = coalesce(item.aliases, [])
      SET e += item.properties
      SET e:$(item.labels)
    """;
  }

  String buildMergeEntityEdgeCypher() {
    var batchParam = Cypher.parameter("batch");
    var item = Cypher.name("item");
    var chunkId = Cypher.name("chunkId");
    Node e = Cypher.node("Entity").named("e");
    Node c = Cypher.node("DocumentChunk").named("c");

    Statement edgeStatement =
        Cypher.unwind(batchParam)
            .as(item)
            .match(e.withProperties("id", item.property("id")))
            .unwind(item.property("chunk_ids"))
            .as(chunkId)
            .match(c.withProperties("chunk_id", chunkId))
            .merge(e.relationshipTo(c, "EXTRACTED_FROM"))
            .build();

    return edgeStatement.getCypher();
  }

  String buildLinkChunksCypher() {
    var docIdParam = Cypher.parameter("docId");
    var chunks = Cypher.name("chunks");
    var i = Cypher.name("i");

    Node current = Cypher.anyNode("current");
    Node nextNode = Cypher.anyNode("next_node");
    Node d = Cypher.node("Document").named("d").withProperties("document_id", docIdParam);
    Node c = Cypher.node("DocumentChunk").named("c");
    Statement linkStatement =
        Cypher.match(d.relationshipFrom(c, "PART_OF"))
            .with(c)
            .orderBy(c.property("chunk_index").ascending())
            .with(Cypher.collect(c).as(chunks))
            .unwind(
                Cypher.range(
                    Cypher.literalOf(0), Cypher.size(chunks).subtract(Cypher.literalOf(2))))
            .as(i)
            .with(
                chunks,
                Cypher.valueAt(chunks, i).as("current"),
                Cypher.valueAt(chunks, i.add(Cypher.literalOf(1))).as("next_node"))
            .merge(current.relationshipTo(nextNode, "NEXT"))
            .returningDistinct(Cypher.size(chunks).as("chunkCount"))
            .build();

    return linkStatement.getCypher();
  }

  String buildMergeRelationshipCypher(String type) {
    var batchParam = Cypher.parameter("batch");
    var item = Cypher.name("item");
    Node src = Cypher.node("Entity").named("src").withProperties("id", item.property("source"));
    Node tgt = Cypher.node("Entity").named("tgt").withProperties("id", item.property("target"));

    org.neo4j.cypherdsl.core.Relationship r = src.relationshipTo(tgt, type).named("r");
    Statement relationshipStatement =
        Cypher.unwind(batchParam)
            .as(item)
            .match(src)
            .match(tgt)
            .merge(r)
            .mutate(r, item.property("props"))
            .build();

    return relationshipStatement.getCypher();
  }

  private Map<String, Object> sanitizeProperties(Map<String, Object> properties) {
    if (properties == null || properties.isEmpty()) return Collections.emptyMap();

    Map<String, Object> sanitized = new HashMap<>();
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      switch (value) {
        case null -> {
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
        case List<?> list when list.isEmpty() -> {
          continue;
        }
        default -> {}
      }

      sanitized.put(key, value);
    }
    return sanitized;
  }
}
