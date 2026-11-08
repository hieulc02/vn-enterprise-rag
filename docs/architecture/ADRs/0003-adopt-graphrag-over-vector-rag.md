# GraphRAG Architecture for Complex Financial Document Reasoning

**Date:** October 2026

## Context

InsightRAG is designed to extract insights from Vietnamese corporate financial documents. These documents are highly
complex, featuring dense tabular data, cross-referenced contracts, and intricate corporate ownership structure spanning
dozens of pages.

A traditional Vector RAG approach relies purely on semantic similarity search over flattened, chunked text. This
struggles significantly in the financial domain. Vector embeddings fail to preserve structural relationships.
Furthermore, Vector RAG cannot reliably answer multi-hop queries because the necessary context is fractured across
isolated vector chunks without explicit relational mapping.

## Decision

We adopted a **GraphRAG architecture**, utilizing Neo4j as the primary knowledge store alongside a hybrid search
retrieval strategy.

During ingestion, the extraction worker parses documents into a hierarchical tree structure to preserve tabular
semantics. An LLM is then prompted to extract explicit financial entities and their relationships, which are
synchronized into Neo4j. During retrieval, the API executes a hybrid search: it uses standard vector embeddings to
locate entry-point nodes in the graph, and then executes Cypher queries to traverse connected relationships, assembling
a deterministic context block for the generation LLM.

## Consequences

**Positive:**

* **Multi-Hop Reasoning:** The system can accurately answer complex, cross-document queries by traversing explicit
  database relationships rather than relying on mathematical vector similarity.
* **Reduced Hallucinations:** Grounding the LLM in a rigid, deterministic graph structure prevents it from fabricating
  financial links that do not exist.
* **Anomaly Detection:** Presenting financial data as a graph enables the detection of structural anomalies, which are
  invisible in standard vector stores.

**Negative:**

* **Ingestion Latency & Cost:** Extracting entities and relationships via an LLM during the ingestion phase is
  computationally expensive and slow compared to simple text embedding.
* **Infrastructure Complexity:** Maintaining Neo4j introduces a steeper learning curve for operations, requiring
  specific expertise in Cypher query optimization.
* **Evaluation Limitation:** Because answers rely on multi-hop traversal of aggregated data, the pipeline is
  incompatible with standard open-source LLM evaluation frameworks and requires custom evaluation heuristics.
