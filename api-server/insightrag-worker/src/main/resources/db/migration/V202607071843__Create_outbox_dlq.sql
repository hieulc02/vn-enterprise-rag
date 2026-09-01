CREATE TABLE worker_schema.outbox_dlq(
    id uuid primary key default gen_random_uuid(),
    failed_payload text not null,
    error_reason text not null,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP,
    is_resolved boolean not null default false
);