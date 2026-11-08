CREATE SCHEMA IF NOT EXISTS worker_schema;

CREATE TABLE IF NOT EXISTS worker_schema.document(
    id uuid primary key,
    file_key varchar(255) not null unique,
    status varchar(50) not null,
    hash_content varchar(64) not null,
    latest_sequencer varchar(100) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    acl_role varchar(50) not null,
    type varchar(20) not null
);

CREATE UNIQUE INDEX IF NOT EXISTS ix_document_file_key ON worker_schema.document(file_key);
CREATE INDEX IF NOT EXISTS ix_document_hash_content ON worker_schema.document(hash_content);

CREATE TABLE IF NOT EXISTS worker_schema.department(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(255) not null
);

CREATE TABLE IF NOT EXISTS worker_schema.document_departments(
    document_id uuid not null REFERENCES worker_schema.document(id) ON DELETE CASCADE,
    department_id bigint not null REFERENCES worker_schema.department(id) ON DELETE CASCADE,

    primary key (document_id, department_id)
);