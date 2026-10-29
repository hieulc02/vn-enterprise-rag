
-- Postgres 16
CREATE USER debezium WITH PASSWORD 'debezium';

ALTER USER debezium WITH REPLICATION;

CREATE SCHEMA IF NOT EXISTS worker_schema;

GRANT USAGE ON SCHEMA worker_schema TO debezium;

-- SELECT privileges on the tables to copy the initial table data
GRANT SELECT ON ALL TABLES IN SCHEMA worker_schema TO debezium;

-- Future table read access
ALTER DEFAULT PRIVILEGES IN SCHEMA worker_schema GRANT SELECT ON TABLES TO debezium;
