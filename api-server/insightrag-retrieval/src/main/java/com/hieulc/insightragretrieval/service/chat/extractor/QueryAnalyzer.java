package com.hieulc.insightragretrieval.service.chat.extractor;

import com.hieulc.insightragretrieval.dto.QueryAnalysis;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface QueryAnalyzer {
  @SystemMessage(
      """
  You are a backend query analyzer for knowledge graph retrieval pipeline.
  Your job is to analyze the user's natural language question, extract keywords for fulltext search,
  identify metadata filters, and rewrite the query to be optimal for vector embeddings.
  Respond ONLY with the requested structured data.
  """)
  QueryAnalysis analyze(@UserMessage String userQuery);
}
