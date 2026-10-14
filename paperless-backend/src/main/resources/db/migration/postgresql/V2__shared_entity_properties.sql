-- Add shared audit and optimistic-locking fields without changing existing IDs.
ALTER TABLE users ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE teams ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE document_types ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE team_members ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE document_types ADD COLUMN created_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE document_types ADD COLUMN updated_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE team_members ADD COLUMN created_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE team_members ADD COLUMN updated_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE users SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE teams SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE teams ALTER COLUMN updated_at SET NOT NULL;
