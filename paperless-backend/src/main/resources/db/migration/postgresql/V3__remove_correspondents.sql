-- Correspondent data is intentionally discarded by this migration.
ALTER TABLE documents DROP CONSTRAINT fk_documents_correspondent;
ALTER TABLE documents DROP COLUMN correspondent_id;
DROP TABLE correspondents;
