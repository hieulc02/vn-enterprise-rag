DROP INDEX IF EXISTS ix_document_hash_content;
DROP INDEX IF EXISTS ix_document_file_key;

CREATE UNIQUE INDEX ix_document_hash_content_active
    ON worker_schema.document (hash_content)
    WHERE status != 'DELETED';

CREATE UNIQUE INDEX ix_document_file_key_active
    ON worker_schema.document (file_key)
    WHERE status != 'DELETED';