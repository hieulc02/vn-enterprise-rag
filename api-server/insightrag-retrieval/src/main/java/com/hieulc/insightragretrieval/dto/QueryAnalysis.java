package com.hieulc.insightragretrieval.dto;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public record QueryAnalysis(
    @Description(
            "A highly descriptive, rewritten version of the query optimized for dense vector search.")
        String optimizedVectorQuery,
    @Description(
            "A list of 3-5 core keywords, acronyms, or proper nouns extracted for exact fulltext search.")
        List<String> extractKeyword,
    @Description("The specific document type the user is looking for. Null if unspecified.")
        String documentType) {}
