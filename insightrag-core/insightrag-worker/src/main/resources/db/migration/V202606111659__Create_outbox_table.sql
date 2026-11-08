DROP TABLE IF EXISTS worker_schema.document_outbox CASCADE;

CREATE TABLE worker_schema.document_outbox(
    id uuid primary key,
    aggregate_type varchar(255) not null,
    aggregate_id varchar(255) not null,
    event_type varchar(255) not null,

    payload jsonb not null
);