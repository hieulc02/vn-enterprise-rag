package com.hieulc.insightragingestion.repository;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GraphRepositoryTest {

  @Test
  void shouldGenerateCorrectMergeChunkCypher() {

    String cypher = GraphRepositoryImpl.buildMergeChunksNodeAndEdgeCypher();

    String expectedCypher =
        """
         MERGE (d:`Document` {document_id: $docId})
         WITH d
         UNWIND $batch AS item
         MERGE (c:`DocumentChunk` {chunk_id: item.chunk_id})
         MERGE (c)-[:`PART_OF`]->(d)
         SET c.text = item.text,
             c.chunk_index = item.chunk_index,
             c.embedding = item.embedding
         SET c += item.metadata
        """;

    assertThat(cypher).isEqualToNormalizingWhitespace(expectedCypher);
  }

  @Test
  void shouldGenerateCorrectMergeEntityCypher() {
    String cypher = GraphRepositoryImpl.buildMergeEntityCypher();

    String expectedCypher =
        """
          UNWIND $batch AS item
          MERGE (e:Node {id: item.id})
          SET e.title = item.title, e.description = item.description
          SET e.aliases = [alias IN coalesce(e.aliases, []) WHERE NOT alias IN coalesce(item.aliases, [])] + coalesce(item.aliases, [])
          SET e += item.properties
          SET e:$(coalesce(item.labels, []))
        """;
    assertThat(cypher).isEqualToNormalizingWhitespace(expectedCypher);
  }

  @Test
  void shouldGenerateCorrectEntityEdgeCypher() {
    String cypher = GraphRepositoryImpl.buildMergeEntityEdgeCypher();

    String expectedCypher =
        """
        UNWIND $batch AS item
        MATCH (e:`Node` {id: item.id})
        UNWIND item.chunk_ids AS chunkId
        MATCH (c:`DocumentChunk` {chunk_id: chunkId})
        MERGE (e)-[:`EXTRACTED_FROM`]->(c)
        """;

    assertThat(cypher).isEqualToNormalizingWhitespace(expectedCypher);
  }

  @Test
  void shouldGenerateCorrectLinkEdgeCypher() {
    String cypher = GraphRepositoryImpl.buildLinkChunksCypher();

    String expectedCypher =
        """
        MATCH (d:`Document` {document_id: $docId})<-[:`PART_OF`]-(c:`DocumentChunk`)
        WITH c ORDER BY c.chunk_index ASC
        WITH collect(c) AS chunks
        WITH chunks, size(chunks) AS chunkCount
        CALL (chunks) {UNWIND range(0, (size(chunks) - 2)) AS i
          WITH chunks[i] AS current, chunks[(i + 1)] AS next_node
          WHERE (current IS NOT NULL AND next_node IS NOT NULL)
          MERGE (current)-[:`NEXT`]->(next_node)}
        RETURN chunkCount
    """;

    assertThat(cypher).isEqualToNormalizingWhitespace(expectedCypher);
  }

  @Test
  void shouldGenerateCorrectMergeRelationshipCypher() {
    String relType = "RELATED_TO";
    String cypher = GraphRepositoryImpl.buildMergeRelationshipCypher(relType);

    String expectedCypher =
        String.format(
            """
        UNWIND $batch AS item
        MATCH (src:`Node` {id: item.source})
        MATCH (tgt:`Node` {id: item.target})
        MERGE (src)-[r:`%s`]->(tgt)
        SET r.source_chunk_ids = ([cid IN coalesce(r.source_chunk_ids, []) WHERE NOT (cid IN coalesce(item.source_chunk_ids, []))] + coalesce(item.source_chunk_ids, []))
        SET r += item.props
        """,
            relType);

    assertThat(cypher).isEqualToNormalizingWhitespace(expectedCypher);
  }
}
