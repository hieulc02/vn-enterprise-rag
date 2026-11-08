package com.hieulc.insightragretrieval.service.chat.cache;

import com.hieulc.insightragretrieval.dto.query.QueryAnalysis;
import com.hieulc.insightragretrieval.dto.query.QueryPreparation;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CachedQueryPreparationService {

  private final CachedQueryAnalyzerService cachedQueryAnalyzer;
  private final EmbeddingModel embeddingModel;

  @Cacheable(value = "queryPreparationCache", key = "#query.trim()", sync = true)
  public QueryPreparation prepare(String query) {
    log.info("Cache miss. Executing query preparation");
    QueryAnalysis analysis = cachedQueryAnalyzer.analyze(query);
    float[] vector = embeddingModel.embed(analysis.optimizedVectorQuery()).content().vector();

    return new QueryPreparation(analysis, vector);
  }
}
