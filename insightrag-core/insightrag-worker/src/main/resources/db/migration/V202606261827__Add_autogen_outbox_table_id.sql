ALTER TABLE worker_schema.document_outbox
ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE worker_schema.document
ALTER COLUMN id SET DEFAULT gen_random_uuid();