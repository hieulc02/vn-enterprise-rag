# Operational Runbook: Local Development & Data Resets

**Status:** Active

**Target:** Local Docker Compose Environment

---

During local development and manual testing of the InsightRAG pipeline, it is common for the databases and message
brokers to enter an inconsistent state.

This runbook provides the standard procedures for safely resetting the infrastructure state without requiring a full
system rebuild.

## 1. Wiping the Neo4j Knowledge Graph

If the Python extraction worker generates a malformed graph, or we need to test the Java ingestion worker from a clean
state, we can wipe the Neo4j database while leaving the rest of the infrastructure intact.

### Option A: Soft Wipe (Cypher Query)

This is the safest method as it keeps the Neo4j container running and preserves database indexes.

1. Open the Neo4j Browser at `http://localhost:7474`.
2. Authenticate using the local `.env` credentials.
3. Execute the following Cypher query to delete all nodes relationships:

```cypher
MATCH (n) DETACH DELETE n;
```

### Option B: Hard Wipe (Docker Volume)

If the database is entirely corrupted, we can destroy and recreate the container volume.

```bash
# Stop the Neo4j container
docker compose stop neo4j

# Remove the container and its associated volume
docker compose rm -v neo4j

# Restart the container 
docker compose up -d neo4j
```

## 2. MinIO Storage Cleanup

Intermediate JSON graphs and raw PDFs can accumulate quickly during testing.

To manually clear the storage:

1. Access the MinIO Console at `http://localhost:9001`.
2. Authenticate using the root credentials.
3. Navigate to the `documents` bucket and use the UI to bulk-delete the objects.




