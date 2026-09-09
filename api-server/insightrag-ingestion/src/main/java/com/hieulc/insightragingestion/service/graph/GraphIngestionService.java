package com.hieulc.insightragingestion.service.graph;

import com.google.common.collect.Lists;
import com.hieulc.insightragingestion.dto.DocumentChunk;
import com.hieulc.insightragingestion.dto.DocumentGraph;
import com.hieulc.insightragingestion.dto.Entity;
import com.hieulc.insightragingestion.dto.Relationship;
import com.hieulc.insightragingestion.repository.GraphCustomRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphIngestionService {

  private final GraphCustomRepository graphRepository;

  @Value("${insightrag.graph.ingestion.batch-size:1000}")
  private int batchSize;

  @Transactional
  public void ingest(DocumentGraph graph) {

    String docId = graph.documentId();
    log.info("Graph ingestion for document: {}", docId);

    List<DocumentChunk> chunks = safeList(graph.chunks());
    List<Entity> entities = safeList(graph.entities());
    List<Relationship> relationships = safeList(graph.relationships());

    if (!chunks.isEmpty()) {
      Lists.partition(chunks, batchSize)
          .forEach(batch -> graphRepository.batchInsertChunks(docId, batch));
      int linked = graphRepository.linkChunks(docId);
      if (linked > 0) {
        log.info("Linked {} chunks ingested for document: {}", linked, docId);
      }
    }

    if (!entities.isEmpty()) {
      Lists.partition(entities, batchSize).forEach(graphRepository::batchInsertEntities);
      log.debug("Ingest {} entities for document: {}", entities.size(), docId);
    }

    if (!relationships.isEmpty()) {
      Lists.partition(relationships, batchSize).forEach(graphRepository::batchInsertRelationship);
      log.debug("Ingest {} relationships for document: {}", relationships.size(), docId);
    }

    log.info("Completed graph ingestion for document: {}", docId);
  }

  private <T> List<T> safeList(List<T> list) {
    return Optional.ofNullable(list).orElseGet(Collections::emptyList);
  }
}
