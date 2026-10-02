package com.hieulc.insightragretrieval.dto.tokenizer;

import com.hieulc.insightragretrieval.exception.infras.TokenExceededLimitException;

public record TokenBudget(int maxModelLimit, int systemTokens, int historyTokens, int queryTokens) {
  public static TokenBudget of(int absoluteLimit, int sys, int hist, int query, int maxQueryLimit) {
    if (query > maxQueryLimit) {
      throw new TokenExceededLimitException(
          String.format("Query token count (%d) exceeds max limit of %d", query, maxQueryLimit));
    }
    return new TokenBudget((int) (absoluteLimit * 0.95), sys, hist, query);
  }

  public int availableForContext() {
    return maxModelLimit - systemTokens - historyTokens - queryTokens;
  }
}
