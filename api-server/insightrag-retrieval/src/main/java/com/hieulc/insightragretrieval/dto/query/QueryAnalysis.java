package com.hieulc.insightragretrieval.dto.query;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public record QueryAnalysis(
    @Description(
            "A dense, standalone statement optimized for vector search. Resolve pronouns to explicit subjects. "
                + "CRITICAL: PRESERVE all brand names, proper nouns, and acronyms EXACTLY as written. DO NOT expand, spell out, or translate terms.")
        String optimizedVectorQuery,
    @Description(
            "2 to 5 high-signal nouns or proper entities for exact matching. "
                + "CRITICAL: Strictly exclude numbers, monetary values, dates, and operational search verbs. Preserve exact spelling.")
        List<String> extractKeyword) {}
