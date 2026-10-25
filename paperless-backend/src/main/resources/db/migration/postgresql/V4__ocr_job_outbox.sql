CREATE TABLE ocr_jobs (
    id uuid NOT NULL,
    document_id uuid NOT NULL,
    storage_key varchar(255) NOT NULL,
    content_type varchar(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_ocr_jobs PRIMARY KEY (id),
    CONSTRAINT uq_ocr_jobs_document UNIQUE (document_id),
    CONSTRAINT fk_ocr_jobs_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
);

CREATE INDEX ix_ocr_jobs_created_at ON ocr_jobs (created_at);
