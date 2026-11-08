package com.hieulc.insightragretrieval.util;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class LuceneQueryUtils {

  private LuceneQueryUtils() {}

  private static final String LUCENE_SPECIAL_CHARS = "+-!(){}[]^\"~*?:\\/|&";

  private static final int MAX_KEYWORDS = 50;

  public static String toOrQuery(List<String> keywords) {
    return buildQuery(keywords, "OR");
  }

  public static String buildQuery(List<String> keywords, String operator) {
    if (keywords == null || keywords.isEmpty()) {
      return "";
    }
    if (keywords.size() > MAX_KEYWORDS) {
      throw new IllegalArgumentException("Keywords list exceeds maximum limit of " + MAX_KEYWORDS);
    }

    return keywords.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(k -> !k.isEmpty())
        .map(LuceneQueryUtils::escapeLucene)
        .map(kw -> "\"" + kw + "\"")
        .collect(Collectors.joining(" " + operator + " "));
  }

  private static String escapeLucene(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    StringBuilder sb = new StringBuilder(input.length() + 16);

    for (char c : input.toCharArray()) {
      if (LUCENE_SPECIAL_CHARS.indexOf(c) != -1) {
        sb.append('\\');
      }
      sb.append(c);
    }
    return sb.toString();
  }
}
