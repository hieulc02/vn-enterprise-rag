CREATE PUBLICATION dbz_publication FOR TABLE worker_schema.document_outbox WITH (publish = 'insert');

