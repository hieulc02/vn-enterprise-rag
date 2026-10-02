package com.hieulc.insightragingestion.repository;

import com.hieulc.insightragingestion.dto.DocumentChunk;
import com.hieulc.insightragingestion.dto.Entity;
import com.hieulc.insightragingestion.dto.Relationship;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Expression;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.Statement;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class GraphRepositoryImpl implements GraphCustomRepository {

  private final Neo4jClient neo4jClient;

  private final String mergeChunksNodeAndEdgeCypher;
  private final String mergeEntityCypher;
  private final String mergeEntityEdgeCypher;
  private final String linkChunksCypher;

  private final Map<String, String> relationshipCypherCache = new ConcurrentHashMap<>();

  public GraphRepositoryImpl(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
    this.mergeChunksNodeAndEdgeCypher = buildMergeChunksNodeAndEdgeCypher();
    this.mergeEntityCypher = buildMergeEntityCypher();
    this.mergeEntityEdgeCypher = buildMergeEntityEdgeCypher();
    this.linkChunksCypher = buildLinkChunksCypher();
  }

  @Override
  @Transactional
  public void batchInsertChunks(String documentId, List<DocumentChunk> chunks) {
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

    neo4jClient
        .query(mergeChunksNodeAndEdgeCypher)
        .bind(batch)
        .to("batch")
        .bind(documentId)
        .to("docId")
        .run();
  }

  @Override
  @Transactional
  public void batchInsertEntities(List<Entity> entities) {
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

    neo4jClient.query(mergeEntityCypher).bind(nodeBatch).to("batch").run();

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
      neo4jClient.query(mergeEntityEdgeCypher).bind(edgeBatch).to("batch").run();
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

          Map<String, Object> batchMap = new HashMap<>();
          batchMap.put("source", rel.source());
          batchMap.put("target", rel.target());
          batchMap.put("props", props);

          if (rel.sourceChunkIds() != null && !rel.sourceChunkIds().isEmpty()) {
            props.put("source_chunk_ids", rel.sourceChunkIds());
          }

          groupedRelationship.computeIfAbsent(rel.type(), k -> new ArrayList<>()).add(batchMap);
        });

    for (Map.Entry<String, List<Map<String, Object>>> entry : groupedRelationship.entrySet()) {
      String type = entry.getKey();
      List<Map<String, Object>> batch = entry.getValue();

      String relationshipCypher =
          relationshipCypherCache.computeIfAbsent(
              type, GraphRepositoryImpl::buildMergeRelationshipCypher);

      neo4jClient.query(relationshipCypher).bind(batch).to("batch").run();
    }
  }

  @Override
  @Transactional
  public int linkChunks(String documentId) {
    return neo4jClient
        .query(linkChunksCypher)
        .bind(documentId)
        .to("docId")
        .fetchAs(Integer.class)
        .all()
        .stream()
        .findFirst()
        .orElse(0);
  }

  static String buildMergeChunksNodeAndEdgeCypher() {
    var docIdParam = Cypher.parameter("docId");
    var batchParam = Cypher.parameter("batch");
    var item = Cypher.name("item");
    Node d = Cypher.node("Document").named("d");
    Node c = Cypher.node("DocumentChunk").named("c");

    return Cypher.merge(d.withProperties("document_id", docIdParam))
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
        .build()
        .getCypher();
  }

  static String buildMergeEntityCypher() {
    // Drop because Cypher-DLS doesn't directly support `apoc.create.addLabels` APOC procedure
    //    var batchParam = Cypher.parameter("batch");
    //    var item = Cypher.name("item");
    //    Node e = Cypher.node("Entity").named("e");
    //    Statement nodeStatement =
    //        Cypher.unwind(batchParam)
    //            .as(item)
    //            .merge(e.withProperties("id", item.property("id")))
    //            .set(
    //                e.property("title").to(item.property("title")),
    //                e.property("description").to(item.property("description")),
    //                e.property("aliases")
    //                    .to(Cypher.coalesce(item.property("aliases"), Cypher.listOf())))
    //            .mutate(e, item.property("properties"))
    //            .with(e, item)
    //            .call("apoc.create.addLabels")
    //            .withArgs((Expression) e, Cypher.coalesce(item.property("labels"),
    // Cypher.listOf()))
    //            .yield("node")
    //            .finish()
    //            .build();
    return """
      UNWIND $batch AS item
      MERGE (e:Node {id: item.id})
      SET e.title = item.title, e.description = item.description
      SET e.aliases = [alias IN coalesce(e.aliases, []) WHERE NOT alias IN coalesce(item.aliases, [])] + coalesce(item.aliases, [])
      SET e += item.properties
      SET e:$(coalesce(item.labels, []))
    """;
  }

  static String buildMergeEntityEdgeCypher() {
    var batchParam = Cypher.parameter("batch");
    var item = Cypher.name("item");
    var chunkId = Cypher.name("chunkId");
    Node e = Cypher.node("Node").named("e");
    Node c = Cypher.node("DocumentChunk").named("c");

    return Cypher.unwind(batchParam)
        .as(item)
        .match(e.withProperties("id", item.property("id")))
        .unwind(item.property("chunk_ids"))
        .as(chunkId)
        .match(c.withProperties("chunk_id", chunkId))
        .merge(e.relationshipTo(c, "EXTRACTED_FROM"))
        .build()
        .getCypher();
  }

  static String buildLinkChunksCypher() {
    var docIdParam = Cypher.parameter("docId");
    var chunks = Cypher.name("chunks");
    var i = Cypher.name("i");
    var chunkCount = Cypher.name("chunkCount");
    var currentName = Cypher.name("current");
    var nextNodeName = Cypher.name("next_node");

    Node current = Cypher.anyNode("current");
    Node nextNode = Cypher.anyNode("next_node");
    Node d = Cypher.node("Document").named("d").withProperties("document_id", docIdParam);
    Node c = Cypher.node("DocumentChunk").named("c");

    Statement subquery =
        Cypher.unwind(
                Cypher.range(
                    Cypher.literalOf(0), Cypher.size(chunks).subtract(Cypher.literalOf(2))))
            .as(i)
            .with(
                Cypher.valueAt(chunks, i).as(currentName),
                Cypher.valueAt(chunks, i.add(Cypher.literalOf(1))).as(nextNodeName))
            .where(current.isNotNull())
            .and(nextNode.isNotNull())
            .merge(current.relationshipTo(nextNode, "NEXT"))
            .build();

    return Cypher.match(d.relationshipFrom(c, "PART_OF"))
        .with(c)
        .orderBy(c.property("chunk_index").ascending())
        .with(Cypher.collect(c).as(chunks))
        .with(chunks, Cypher.size(chunks).as(chunkCount))
        .call(subquery, chunks)
        .returning(chunkCount)
        .build()
        .getCypher();
  }

  static String buildMergeRelationshipCypher(String type) {
    var batchParam = Cypher.parameter("batch");
    var item = Cypher.name("item");
    var cid = Cypher.name("cid");

    Node src = Cypher.node("Node").named("src").withProperties("id", item.property("source"));
    Node tgt = Cypher.node("Node").named("tgt").withProperties("id", item.property("target"));

    Expression emptyList = Cypher.listOf();
    Expression itemChunkIds = Cypher.coalesce(item.property("source_chunk_ids"), emptyList);

    org.neo4j.cypherdsl.core.Relationship r = src.relationshipTo(tgt, type).named("r");

    Expression cidList =
        Cypher.listWith(cid)
            .in(Cypher.coalesce(r.property("source_chunk_ids"), emptyList))
            .where(cid.in(itemChunkIds).not())
            .returning();

    return Cypher.unwind(batchParam)
        .as(item)
        .match(src)
        .match(tgt)
        .merge(r)
        .set(r.property("source_chunk_ids").to(cidList.add(itemChunkIds)))
        .mutate(r, item.property("props"))
        .build()
        .getCypher();
  }

  private static Map<String, Object> sanitizeProperties(Map<String, Object> properties) {
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
