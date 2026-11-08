# InsightRAG Infrastructure & Deployment

This directory manage the container orchestration, network isolation, reverse proxy routing, and production resource
governance for **InsightRAG**.

## 1. Directory Structure

```text
docker/
├── auth/                                         # Keycloak 26.8.1 & realm import definitions
├── data/                                         # PostgreSQL 16 (pgvector + CDC WAL) & Kafka (KRaft)
├── gateway/                                      # Nginx reverse proxy (SSE unbuffered routing & Actuator shield)
├── graph/                                        # Neo4j (APOC) & Infinity Embedding (Vietnamese Embedding)
├── scripts/                                      # Idempotent bootstrap scripts (Kafka topics, MinIO buckets, DB)
├── storage/                                      # MinIO object storage & RabbitMQ broker 
├── compose.prod.yaml                             # Production overlay (cgroups limits, port stripping)
├── compose.yaml                                  # Base topology & local build definitions
└── manage.sh                                     # Pass-through CLI wrapper for profile & environment layering
```

## 2. Environment & Secret Contract (`.env`)

Copy `.env.example` to `.env` inside `docker/` (and ensure service-specific `.env` file exist in `../api-server/.env`
and `../doc-worker/.env` for local `dev` mode).

`compose.prod.yml` enforces **fail-fast variable validation** (`${VAR:?error)`). Deployment will immediately abort if
any of the following variables are unset:

```properties 
# Registry & Versioning (Used in prod mode to pull from GHCR)
GITHUB_OWNER=github-username
IMAGE_TAG=latest
# PostgreSQL & Debezium CDC
POSTGRES_USER=insightrag
POSTGRES_PASSWORD=change_me_in_prod
POSTGRES_DB=insightrag_db
DEBEZIUM_DB_USER=insightrag
DEBEZIUM_DB_PASSWORD=change_me_in_prod
# RabbitMQ & MinIO Object Storage
RABBITMQ_DEFAULT_USER=rabbitmq
RABBITMQ_DEFAULT_PASS=change_me_in_prod
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=change_me_in_prod
# Neo4j Graph & Keycloak Auth
GRAPH_PASSWORD=change_me_in_prod
KEYCLOAK_PUBLIC_URL=http://localhost:9090
```

## 3. Startup & Bootstrap Sequence

**1. Stateful Storage & Brokers Boot First:** `postgres-db`, `broker` (Kafka), `rabbitmq`, and `neo4j` initialize their
storage volumes and must pass internal health probes before dependent services start.

**2. First-Run Volume Initialization:**

- `postgres-db` executes SQL scripts in `data/init-db/` (`create_keycloak_db.sql`, `cdc_init_replication_security.sql`)
  only on initial volume creation.
- `broker-setup` runs `scripts/kafka-topics-init.sh` once `broker` is healthy, creating required Kafka topics
  idempotently before exiting.
- `minio` waits for `rabbitmq` to report healthy so its AMQP bucket notification target (`main.direct.exchange`) is
  reachable on boot.

**3. Model & Realm Warmup:** `infinity-embedding` downloads/loads `AITeamVN/Vietnamese_Embedding` into memory (
`--model-warmup`), and `keycloak` imports `insightrag-realm.json`.

**4. Application & Gateway Activation:** `insightrag-worker`, `insightrag-worker`, `insightrag-ingestion`, and
`insightrag-retrieval` connect to the healthy infrastructure, followed by `api-gateway` (Nginx) opening port `80`.

## 4. Operation Workflows (`manage.sh`)

Requires **Docker Engine v25.0+** and **Docker Compose v2.24.6+** (for `!override` and `!reset` tag support).

```bash
chmod +x manage.sh script/*.sh
```

**Usage Syntax**

```bash
./manage.sh <dev|prod> <all|worker|extraction|ingestion|retrieval> <docker-compose-args...>
```

**Local Development (`dev`)**
Builds images locally from `../api-server` and `../doc-worker` and exposes infrastructure port to `localhost` for IDE
debugging:

```bash
# Start full stack in background
./manage.sh dev all up -d

# Start only the Retrieval API and its required dependencies
./manage.sh dev retrieval up -d

# Rebuild a single service after code modification
./manage.sh dev worker up -d --build insightrag-worker

# Tail live logs for a profile
./manage.sh dev extraction logs -f --tail=100
```

**Production Deployment (`prod`)**

Strips `build:` blocks (`!reset null`), pull immutable images from `ghcr.io/${GITHUB_OWNER}/...:${IMAGE_TAG}`, seals
internal ports, and enforces `cgroup v2` CPU/RAM limits:

```bash
# 1. Validate merged production YAML and fait-fast secret 
./manage.sh prod all config -q

# 2. Pull pre-built image from Github Container Registry
./manage.sh prod all config -q

# 3. Perform rolling update of changed container
./manage.sh prod all up -d --remove-orphans

# 4. Verify all containers report 
./manage.sh prod all up ps
```

## 5. Network Routing & Port Access Matrix

| Service                     | Internal DNS (insightrag_net)      | `dev` Host Port             | `prod` Exposure Strategy                     |
|-----------------------------|------------------------------------|-----------------------------|----------------------------------------------|
| **API Gateway (Nginx)**     | `http://api-gateway:80`            | `localhost:80`              | **Public (`0.0.0.0:80`)**                    |                      
| **Retrieval API**           | `http://insightrag-retrieval:8080` | `localhost:8080`            | Routed via Nginx `/api/*` (SSE unbuffered)   |                      
| **Keycloak Auth**           | `http://keycloak:8080`             | `localhost:9090`            | Routed via Nginx `/realms/*`                 |                      
| **Neo4j (UI / Bolt)**       | `bolt://neo4j:7687`                | `localhost:7474` / `:7687`  | Bound to `127.0.0.1` (Access via SSH Tunnel) |                      
| **MinIO (API / UI)**        | `http://minio:9000`                | `localhost:9000` / `:9001`  | Internal (`127.0.0.1:9001` optional via SSH) |                      
| **PostgreSQL (`pgvector`)** | `postgres-db:5432`                 | `localhost:5432`            | Sealed (`ports: !reset []`)                  |                      
| **Kafka (KRaft)**           | `broker:29092`                     | `localhost:9092`            | Sealed (`ports: !reset []`)                  |                      
| **RabbitMQ**                | `rabbitmq:5672`                    | `localhost:5672` / `:15672` | Sealed (`ports: !reset []`)                  |                      
| **Infinity Embedding**      | `http://infinity-embedding:7997`   | `localhost:8081`            | Sealed (`ports: !reset []`)                  |                      

**SSH Tunneling for Production Admin Consoles:**

```bash
ssh -L 7474:127.0.0.1:7474 -L 7687:127.0.0.1:7687 user@<prod-server-id>
```

## 6. Hardware Sizing & Troubleshooting

**Production VM Requirement (`compose.prod.yaml`)**

- **All Profiles combined**: 8 vCPUs, 16-20 GB RAM, 50 GB NVMe SSD.
- **Split-Node Option:** Run `--profile retrieval` on VM 1 (4 vCPUs, 8 GB RAM) and
  `--profile worker --profile extraction --profile ingestion` on VM 2 (4-6 vCPUs, 12 GB RAM)

**Common Operational Issues**

- **Init SQL scripts (`data/init-db/`) did not run after changes:** PostgreSQL only runs `/docker-entrypoint-initdb.d`
  when data directory is empty. In `dev`, run `.manage.sh dev all down -v` to wipe volumes and re-initialize.
- **Container exits immediately with Code `137`:** The Linux kernel OOM killer terminated the container for exceeding
  its `deploy.resources.limits.memory` ceiling. Inspect `docker inspect <container> --format '{{.State.OOMKilled}}'` and
  adjust limits in `compose.prod.yaml`.

