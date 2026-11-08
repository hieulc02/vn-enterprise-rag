package com.hieulc.insightragingestion.service.graph;

import com.hieulc.insightragingestion.dto.graph.*;
import com.hieulc.insightragingestion.repository.graph.GraphCustomRepository;
import com.hieulc.insightragingestion.util.CollectionUtils;
import java.util.Collections;
import java.util.List;
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
  public void ingest(String documentId, DocumentGraph graph) {

    String documentKey = graph.documentKey();
    log.info("Graph ingestion for document: {}", documentKey);

    List<DocumentChunk> chunks = safeList(graph.chunks());
    List<Entity> entities = safeList(graph.entities());
    List<Relationship> relationships = safeList(graph.relationships());

    if (!chunks.isEmpty()) {
      CollectionUtils.partition(chunks, batchSize)
          .forEach(batch -> graphRepository.batchInsertChunks(documentId, documentKey, batch));
      int linked = graphRepository.linkChunks(documentId);
      if (linked > 0) {
        log.info("Linked {} chunks ingested for document: {}", linked, documentKey);
      }
    }

    if (!entities.isEmpty()) {
      CollectionUtils.partition(entities, batchSize).forEach(graphRepository::batchInsertEntities);
      log.debug("Ingest {} entities for document: {}", entities.size(), documentKey);
    }

    if (!relationships.isEmpty()) {
      CollectionUtils.partition(relationships, batchSize).forEach(graphRepository::batchInsertRelationship);
      log.debug("Ingest {} relationships for document: {}", relationships.size(), documentKey);
    }

    log.info("Completed document graph ingestion for document: {}", documentKey);
  }

  @Transactional
  public void deleteDocumentGraph(String documentId) {
    GraphDeleteSummary summary = graphRepository.deleteDocument(documentId);

    if (summary.wasAlreadyDeleted()) {
      log.info("Document {} was already deleted", documentId);
    } else {
      log.info("Completed document graph deletion for document: {}", documentId);
    }
  }

  private <T> List<T> safeList(List<T> list) {
    return list == null ? Collections.emptyList() : list;
  }
}
