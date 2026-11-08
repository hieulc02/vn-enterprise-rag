package com.hieulc.insightragretrieval.service.chat.cache;

import com.hieulc.insightragretrieval.dto.query.QueryAnalysis;
import com.hieulc.insightragretrieval.service.chat.extractor.QueryAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CachedQueryAnalyzerService {
  private final QueryAnalyzer queryAnalyzer;

  @Cacheable(value = "queryAnalysisCache", key = "#query.trim()", sync = true)
  public QueryAnalysis analyze(String query) {
    log.info("LLM cache miss. Executing AI analysis for query: {}", query);
    return queryAnalyzer.analyze(query);
  }
}
