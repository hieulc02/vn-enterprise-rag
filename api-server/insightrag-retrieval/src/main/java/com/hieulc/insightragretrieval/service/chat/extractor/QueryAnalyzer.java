package com.hieulc.insightragretrieval.service.chat.extractor;

import com.hieulc.insightragretrieval.dto.query.QueryAnalysis;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface QueryAnalyzer {
  @SystemMessage(
      """
      You are a query preprocessor for a financial Graph RAG engine.
      Analyze the input query and extract the required search parameters.
      Respond ONLY with a valid JSON object matching the provided schema. Do not include markdown formatting or explanations.
  """)
  QueryAnalysis analyze(@UserMessage String userQuery);
}
