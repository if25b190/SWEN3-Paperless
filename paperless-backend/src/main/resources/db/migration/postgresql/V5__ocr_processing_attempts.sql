ALTER TABLE ocr_jobs
    ADD COLUMN attempt_id uuid,
    ADD COLUMN attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN available_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX ix_ocr_jobs_available_at ON ocr_jobs (available_at, created_at);

-- Recover work dispatched by the previous backend, which deleted jobs on publish.
INSERT INTO ocr_jobs (id, document_id, storage_key, content_type, created_at, updated_at, version)
SELECT gen_random_uuid(), d.id, d.storage_key, d.content_type, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM documents d
WHERE d.status IN ('PENDING', 'OCR_IN_PROGRESS') AND d.storage_key IS NOT NULL
ON CONFLICT (document_id) DO NOTHING;
