package com.hieulc.insightragingestion.repository;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphRepositoryTest {

  private GraphRepositoryImpl graphRepository;

  @BeforeEach
  void setUp() {
    graphRepository = new GraphRepositoryImpl(null);
  }

  @Test
  void shouldGenerateCorrectMergeChunkCypher() {

    String cypher = graphRepository.buildMergeChunksNodeAndEdgeCypher();

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
    String cypher = graphRepository.buildMergeEntityCypher();

    String expectedCypher =
        """
        UNWIND $batch AS item
        MERGE (e:Entity {id: item.id})
        SET e.title = item.title, e.description = item.description
        SET e.aliases = coalesce(item.aliases, [])
        SET e += item.properties
        SET e:$(item.labels)
        """;
    assertThat(cypher).isEqualToNormalizingWhitespace(expectedCypher);
  }

  @Test
  void shouldGenerateCorrectEntityEdgeCypher() {
    String cypher = graphRepository.buildMergeEntityEdgeCypher();

    String expectedCypher =
        """
        UNWIND $batch AS item
        MATCH (e:`Entity` {id: item.id})
        UNWIND item.chunk_ids AS chunkId
        MATCH (c:`DocumentChunk` {chunk_id: chunkId})
        MERGE (e)-[:`EXTRACTED_FROM`]->(c)
        """;

    assertThat(cypher).isEqualToNormalizingWhitespace(expectedCypher);
  }

  @Test
  void shouldGenerateCorrectLinkEdgeCypher() {
    String cypher = graphRepository.buildLinkChunksCypher();

    String expectedCypher =
        """
        MATCH (d:`Document` {document_id: $docId})<-[:`PART_OF`]-(c:`DocumentChunk`)
        WITH c ORDER BY c.chunk_index ASC
        WITH collect(c) AS chunks
        UNWIND range(0, (size(chunks) - 2)) AS i
        WITH chunks, chunks[i] AS current, chunks[(i + 1)] AS next_node
        MERGE (current)-[:`NEXT`]->(next_node)
        RETURN size(chunks) AS chunkCount
       """;

    assertThat(cypher).isEqualToNormalizingWhitespace(expectedCypher);
  }

  @Test
  void shouldGenerateCorrectMergeRelationshipCypher() {
    String relType = "RELATED_TO";
    String cypher = graphRepository.buildMergeRelationshipCypher(relType);

    String expectedCypher =
        String.format(
            """
        UNWIND $batch AS item
        MATCH (src:`Entity` {id: item.source})
        MATCH (tgt:`Entity` {id: item.target})
        MERGE (src)-[r:`%s`]->(tgt)
        SET r += item.props
        """,
            relType);

    assertThat(cypher).isEqualToNormalizingWhitespace(expectedCypher);
  }
}
