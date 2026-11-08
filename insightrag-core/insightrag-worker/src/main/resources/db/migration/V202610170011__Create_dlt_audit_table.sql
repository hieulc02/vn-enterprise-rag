CREATE TABLE IF NOT EXISTS worker_schema.kafka_dlt_audit
(
    id                 uuid primary key         default gen_random_uuid(),
    message_key        varchar(50)  not null,
    original_topic     varchar(255) not null,
    original_partition int          not null,
    original_offset    bigint       not null,
    exception_message  text,
    payload            jsonb,                                             -- stores the raw message, malformed or not
    status             varchar(50)              default 'PENDING_REVIEW', -- PENDING_REVIEW, REPLAYED, DISCARDED
    created_at         timestamp with time zone default CURRENT_TIMESTAMP,

    -- This constraint guarantees idempotency. Kafka cannot have two messages
    -- with the exact same key, topic, partition, and offset.
    CONSTRAINT uk_kafka_dlt_audit UNIQUE (message_key, original_topic, original_partition, original_offset)
);

