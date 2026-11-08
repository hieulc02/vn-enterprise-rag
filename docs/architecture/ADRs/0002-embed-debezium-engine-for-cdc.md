# Guaranteed Event Delivery via Embedded Debezium Engine & Transactional Outbox

**Date:** October 2026

## Context

To eliminate the dual-write problem during document ingestion, the InsightRAG pipeline requires a Transactional Outbox
pattern paired with Change Data Capture (CDC) to read the PostgreSQL Write-Ahead Log (WAL).

The industry standard for deploying Debezium is via a standalone Kafka Connect cluster. However, provisioning a
dedicated distributed JVM for Kafka Connect-alongside Kafka broker, Zookeeper/KRaft, PostgreSQL, Neo4j, MinIO and our
worker services-creates severe memory and CPU constraints for a single-node local development environment. For this
project's scope, the infrastructure bloat of maintaining a distinct Kafka Connect cluster is disproportionate to the
required CDC throughput.

## Decision

We will bypass Kafka Connect and utilize the **Embedded Debezium Engine** directly within the Java orchestration
worker (`insightrag-worker`).

The orchestration service will self-host the CDC process on a dedicated background thread. To guarantee at-least-once
delivery without the safety nets of a dedicated Kafka Connect cluster, the embedded implementation must enforce a
strict, fail-fast batch processing model. If the application fails to publish an extracted WAL payload to Kafka, the
embedded engine must halt immediately and intentionally refuse to commit the WAL offset. This ensures the database and
the message broker remain synchronized, relying on application restarts to resume processing without data loss.

## Consequences

**Positive:**

* **Infrastructure Reduction:** Eliminating the Kafka Connect cluster drastically reduces the container footprint, RAM
  overhead, and the startup time, allowing the entire architecture to run comfortably on a single machine via Docker
  Compose.
* **Deployment Simplicity:** Consolidates deployment boundaries by keeping all Java orchestration and event-publishing
  logic contained within a single deployable artifact.
* **Programmatic Control:** Bypassing standard connectors allows us to write custom, granular dead-letter routing for
  malformed payloads directly in the host application language.

**Negative:**

* **Architectural Coupling:** The core orchestration API is now tightly coupled to the CDC infrastructure. A fatal error
  or memory leak in the CDC tailing thread directly impacts the stability of the host API service.
* **Lost of Ecosystem Tooling:** We forfeit the out-of-the-box management REST APIs, offset tracking, JMX metrics, and
  configuration UI that a standard Connect cluster provides.
* **Horizontal Scalability Bottleneck:** The embedded engine assumes a singleton architecture. If the
  `insightrag-worker` is eventually scaled to multiple instances, we must implement custom leader election to prevent
  multiple instances from simultaneously tailing the exact PostgreSQL replication slot.  
    
