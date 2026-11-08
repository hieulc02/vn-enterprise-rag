# InsightRAG Backend Services

This directory contains the Java Spring boot orchestration and API layers for the InsightRAG system. It is structured as
a Maven multi-module project to enforce strict boundary separation between infrastructure, background event processing,
and synchronous data retrieval.

## Module & Architecture Design

The backend selectively applies architectural patterns based on domain complexity, separating strict data-consistency
rules from straightforward querying.

### 1. `core-infrastructure` (Shared Library)

* **Responsibility:** Foundational layer for shared configurations.
* **Domain Boundaries:** Houses global infrastructure exceptions and centralizes cross-cutting connection properties (
  MinIO storage configuration). It is self-contained and does not depend on any internal modules.

### 2. `insightrag-worker` (Outbox & Orchestration)

* **Architecture:** Hexagonal Architecture (Ports and Adapters).
* **Responsibility:** Document ingestion and downstream event orchestration.
* **I/O Flow:** Consumes storage webhooks, safely persists document metadata to PostgreSQL, stages Transactional Outbox
  events for the embedded Debezium CDC engine.

### 3. `insightrag-ingestion` (Asynchronous Consumer)

* **Architecture:** Layered Architecture.
* **Responsibility:** Knowledge graph synchronization.
* **I/O Flow:** Consumes Kafka `doc-processed-events`, retrieves the extracted JSON graphs from object storage, and
  executes bulk Cypher updates to synchronize the Neo4j database.

### 4. `insightrag-retrieval` (Synchronous API)

* **Architecture:** Layered Architecture.
* **Responsibility:** User-facing query execution.
* **I/O Flow:** Exposes secure REST and SSE endpoints, delegates embedding generation, and execute multi-hop hybrid
  search queries against the Neo4j graph using LangChain4j.

## Prerequisites & Configuration

* **Java 21**
* **Maven 3.9.15** (or use the provided `./mvnw` wrapper)
* **Docker Daemon:** Must be running locally to execute in tests via Testcontainers.

### Environment Variables

Local configuration is managed via `.env` files.

1. Copy the template: `cp .env.example .env`
2. Populate the `.env` file with your local PostgreSQL, Kafka, MinIO, and Neo4j credentials.

## Build & Testing Strategy

The repository employs a comprehensive testing strategy utilizing JUnit5, Mockito for unit testing, and Testcontainer
for isolated integration testing against real database instances.

**Build the entire project (skipping tests):**

```bash
./mvnw clean package -DskipTests
```

**Run the full test suite (verifies only unit tests):**

```bash
./mvnw test
```

**Build and test a specific module (verifies Docker/Testcontainers setup):**

```bash
./mvnw clean verify -pl insightrag-worker -am
```

## Database Migration (Flyway)

Database schema management is exclusively handled by Flyway within the `insightrag-worker` module.

**Important Boot Order:** To prevent schema synchronization errors on a fresh database, we must boot the
`insightrag-worker` module first so Flyway can establish the tables before the other modules attempt to connect.

## Local Development & Execution

Because `insightrag-worker`, `insightrag-ingestion`, and `insightrag-retrieval` are independent Spring Boot
applications, they must be booted separately. Ensure infrastructure (Docker Compose) is running first.

Ensure the backing infrastructure (Docker Compose) is running before starting the applications.

**1. Build the Shared Infrastructure:**

```bash
./mvnw clean install -DskipTests
```

**2. Start the services:**

**Start the Worker Service:**

```bash
./mvnw spring-boot:run -pl insightrag-worker 
```

**Start the Ingestion Service:**

```bash
./mvnw spring-boot:run -pl insightrag-ingestion 
```

**Start the Retrieval API:**

```bash
./mvnw spring-boot:run -pl insightrag-retrieval 
```