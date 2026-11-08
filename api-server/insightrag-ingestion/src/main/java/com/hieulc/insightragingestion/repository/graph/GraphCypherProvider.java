package com.hieulc.insightragingestion.repository.graph;

import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Expression;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.Statement;

final class GraphCypherProvider {
  private GraphCypherProvider() {}

  static String buildMergeChunksNodeAndEdgeCypher() {
    String chunkIndex = "chunk_index";
    String chunkId = "chunk_id";

    var docKeyParam = Cypher.parameter("docKey");
    var docIdParam = Cypher.parameter("docId");
    var batchParam = Cypher.parameter("batch");
    var item = Cypher.name("item");
    Node d = Cypher.node("Document").named("d");
    Node c = Cypher.node("DocumentChunk").named("c");

    return Cypher.merge(d.withProperties("document_id", docIdParam, "document_key", docKeyParam))
        .with(d)
        .unwind(batchParam)
        .as(item)
        .merge(c.withProperties(chunkId, item.property(chunkId)))
        .merge(c.relationshipTo(d, "PART_OF"))
        .set(
            c.property("text").to(item.property("text")),
            c.property(chunkIndex).to(item.property(chunkIndex)),
            c.property("embedding").to(item.property("embedding")))
        .mutate(c, item.property("metadata"))
        .build()
        .getCypher();
  }

  static String buildMergeEntityCypher() {
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
    String sourceChunkIds = "source_chunk_ids";

    var batchParam = Cypher.parameter("batch");
    var item = Cypher.name("item");
    var cid = Cypher.name("cid");

    Node src = Cypher.node("Node").named("src").withProperties("id", item.property("source"));
    Node tgt = Cypher.node("Node").named("tgt").withProperties("id", item.property("target"));

    Expression emptyList = Cypher.listOf();
    Expression itemChunkIds = Cypher.coalesce(item.property(sourceChunkIds), emptyList);

    org.neo4j.cypherdsl.core.Relationship r = src.relationshipTo(tgt, type).named("r");

    Expression cidList =
        Cypher.listWith(cid)
            .in(Cypher.coalesce(r.property(sourceChunkIds), emptyList))
            .where(cid.in(itemChunkIds).not())
            .returning();

    return Cypher.unwind(batchParam)
        .as(item)
        .match(src)
        .match(tgt)
        .merge(r)
        .set(r.property(sourceChunkIds).to(cidList.add(itemChunkIds)))
        .mutate(r, item.property("props"))
        .build()
        .getCypher();
  }

  static String buildDeleteDocumentCypher() {
    return """
        CYPHER 25
        MATCH (d:Document {document_id: $docId})
        OPTIONAL MATCH (c:DocumentChunk)-[:PART_OF]->(d)
        WITH d, collect(c) AS chunks, collect(c.chunk_id) AS deletedChunkIds

        OPTIONAL MATCH (e:Node)-[:EXTRACTED_FROM]->(chunk)
        WHERE chunk IN chunks
        WITH d, chunks, deletedChunkIds, collect(DISTINCT e) AS affectedEntities

        CALL (affectedEntities, deletedChunkIds) {
          UNWIND affectedEntities AS e
          MATCH (e)-[r]-()
          WHERE
            any(cid IN coalesce(r.source_chunk_ids, []) WHERE cid IN deletedChunkIds)

          SET
            r.source_chunk_ids =
              [x IN r.source_chunk_ids WHERE NOT x IN deletedChunkIds]
          WITH r
          WHERE size(r.source_chunk_ids) = 0
          DELETE r
        }

        CALL (chunks) {
          UNWIND chunks AS c
          DETACH DELETE c
        }

        CALL (affectedEntities) {
          UNWIND affectedEntities AS e
          OPTIONAL MATCH (e)-[ex:EXTRACTED_FROM]->(:DocumentChunk)
          WITH e, count(ex) AS remainingLinks
          WHERE remainingLinks = 0
          DETACH DELETE e
        }

        DETACH DELETE d
    """;
  }
}
