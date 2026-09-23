package com.hieulc.insightragretrieval.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LuceneQueryUtilsTest {

  @ParameterizedTest(name = "Test {index}: {3}")
  @MethodSource("provideQueryTestCases")
  void should_build_lucene_query_correctly(
      List<String> keywords, String operator, String expected, String testName) {
    String actual = LuceneQueryUtils.buildQuery(keywords, operator);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void should_throw_exception_when_max_keywords_exceeded() {
    List<String> keywords = IntStream.range(0, 51).mapToObj(String::valueOf).toList();

    assertThatThrownBy(() -> LuceneQueryUtils.buildQuery(keywords, "OR"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Keywords list exceeds maximum limit of 50");
  }

  @Test
  void should_default_to_orQuery() {
    String query = LuceneQueryUtils.toOrQuery(List.of("entity", "subject"));
    assertThat(query).isEqualTo("entity OR subject");
  }

  private static Stream<Arguments> provideQueryTestCases() {
    return Stream.of(
        Arguments.of(List.of("entity", "subject"), "OR", "entity OR subject", "Basic Lucene Query"),
        Arguments.of(null, "OR", "", "Null list return empty string"),
        Arguments.of(Collections.emptyList(), "OR", "", "Empty list returns empty string"),
        Arguments.of(
            Arrays.asList(" entity ", "  ", "subject ", null),
            "AND",
            "entity AND subject",
            "Trim space and drop null/empty string"),
        Arguments.of(List.of("C++", "C#"), "OR", "C\\+\\+ OR C#", "Escapes + but leaves #"),
        Arguments.of(
            List.of("entity@gmail.com", "100%"),
            "AND",
            "entity@gmail.com AND 100%",
            "Ignores non-Lucene special chars"),
        Arguments.of(
            List.of("!(){}"), "OR", "\\!\\(\\)\\{\\}", "Escapes brackets and exclamation"));
  }
}
