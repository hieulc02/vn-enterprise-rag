# Idempotent Document Upserts via Sequencer Checking for Out-of-Order Events

**Date:** October 2026

## Context

The InsightRAG ingestion pipeline separates document upload and document delete events into distinct infrastructure
stream via MinIO bucket notifications. Because of network latency, message broker retries, and concurrent processing, we
face a classic distributed system race condition: bucket notifications for the same document can arrive out of
chronological order.

Relying on distributed locks to sequence the events introduces unacceptable latency and infrastructure overhead.
Conversely, relying on standard ORM application-level versioning requires expensive read-before-write operations and is
vulnerable to clock drift across worker nodes.

## Decision

We implemented optimistic database-level concurrency control utilizing PostgreSQL's native `ON CONFLICT` capabilities
tied to MinIO's immutable event sequencer.

MinIO natively generates a `sequencer` string for every bucket mutation, providing strict chronological ordering. We
configured the `insightrag-worker` repository to bypass standard ORM lifecycle events and execute raw SQL upserts. The
upsert explicitly evaluates the condition: `worker_schema.document.latest_sequencer < EXCLUDED.latest_sequencer`. If an
incoming event possesses an older sequencer than the currently persisted record, the database engine discards the update
entirely.

## Consequences

**Positive:**

* **Strict Idempotency:** The worker can safely process duplicate or out-of-order events without corrupting the
  document's true state, eliminating the need for application-side synchronization.
* **High Performance:** Pushing the sequence evaluation directly down to the PostgreSQL engine eliminates
  read-before-write latency and avoids the need for a distributed locking mechanism.

**Negative:**

* **Database Coupling:** The implementation relies heavily on PostgreSQL-specific syntax (`ON CONFLICT DO UPDATE`),
  meaning a future migration to a different relational database would require rewriting this data access layer.
* **Framework Bypass:** Dropping down to `JdbcClient` means we forfeit automatic Spring data JPA entity auditing,
  requiring manual timestamp management in the SQL queries.  