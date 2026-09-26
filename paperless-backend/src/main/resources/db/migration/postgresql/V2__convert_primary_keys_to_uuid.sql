-- PostgreSQL executes this migration transactionally through Flyway. All DDL
-- here is transactional; do not split this migration or run it with writes
-- still enabled by another application instance. The explicit table locks
-- keep every legacy ID and reference stable while the mapping is built.
--
-- Existing installations must be explicitly baselined at V1 only after the
-- V1 schema has been reviewed and backed up (see the migration README). This migration
-- validates legacy bigint columns, aborts on orphans, maps every ID once, and
-- rolls back all DDL/data changes together on any error.

DO $$
DECLARE
    mismatch record;
BEGIN
    SELECT expected.table_name, expected.column_name, actual.data_type
      INTO mismatch
      FROM (VALUES
          ('users', 'id'),
          ('teams', 'id'), ('teams', 'owner_id'),
          ('team_members', 'id'), ('team_members', 'team_id'), ('team_members', 'user_id'),
          ('correspondents', 'id'),
          ('document_types', 'id'),
          ('documents', 'id'), ('documents', 'owner_id'), ('documents', 'team_id'),
          ('documents', 'correspondent_id'), ('documents', 'document_type_id'),
          ('access_tokens', 'user_id')
      ) AS expected(table_name, column_name)
      LEFT JOIN information_schema.columns AS actual
        ON actual.table_schema = current_schema()
       AND actual.table_name = expected.table_name
       AND actual.column_name = expected.column_name
     WHERE actual.column_name IS NULL OR actual.data_type <> 'bigint'
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'UUID migration requires legacy BIGINT column %.% (found type: %)',
            mismatch.table_name, mismatch.column_name, COALESCE(mismatch.data_type, '<missing>');
    END IF;
END
$$;

CREATE TEMPORARY TABLE _uuid_migration_targets (
    table_name text PRIMARY KEY,
    relation_oid regclass NOT NULL
) ON COMMIT DROP;

INSERT INTO _uuid_migration_targets (table_name, relation_oid)
SELECT table_name, format('%I.%I', current_schema(), table_name)::regclass
  FROM (VALUES
      ('users'), ('teams'), ('team_members'), ('correspondents'),
      ('document_types'), ('documents'), ('access_tokens')
  ) AS target(table_name);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM pg_constraint AS fk
         WHERE fk.contype = 'f'
           AND fk.confrelid IN (
               SELECT relation_oid
                 FROM _uuid_migration_targets
                WHERE table_name IN ('users', 'teams', 'team_members', 'correspondents', 'document_types', 'documents')
           )
           AND fk.conrelid NOT IN (SELECT relation_oid FROM _uuid_migration_targets)
    ) THEN
        RAISE EXCEPTION 'UUID migration found an unhandled foreign key referencing a converted table';
    END IF;
END
$$;

LOCK TABLE users, teams, team_members, correspondents, document_types, documents, access_tokens
    IN ACCESS EXCLUSIVE MODE;

-- Save user-created indexes before replacing columns. Constraint-backed
-- indexes are managed by the PK/unique constraint rebuild below; all other
-- indexes on the migrated tables are recreated from PostgreSQL's definition.
CREATE TEMPORARY TABLE _uuid_migration_indexes (
    schema_name text NOT NULL,
    index_name text NOT NULL,
    index_definition text NOT NULL
) ON COMMIT DROP;

INSERT INTO _uuid_migration_indexes (schema_name, index_name, index_definition)
SELECT index_schema.nspname, index_relation.relname, pg_get_indexdef(index_info.indexrelid)
  FROM pg_index AS index_info
  JOIN _uuid_migration_targets AS target ON target.relation_oid = index_info.indrelid
  JOIN pg_class AS index_relation ON index_relation.oid = index_info.indexrelid
  JOIN pg_namespace AS index_schema ON index_schema.oid = index_relation.relnamespace
 WHERE NOT EXISTS (
       SELECT 1
         FROM pg_constraint AS constraint_info
        WHERE constraint_info.conindid = index_info.indexrelid
  );

DO $$
DECLARE
    index_row record;
BEGIN
    FOR index_row IN SELECT schema_name, index_name FROM _uuid_migration_indexes LOOP
        EXECUTE format('DROP INDEX %I.%I', index_row.schema_name, index_row.index_name);
    END LOOP;
END
$$;

CREATE TEMPORARY TABLE _uuid_migration_counts (
    table_name text PRIMARY KEY,
    row_count bigint NOT NULL
) ON COMMIT DROP;

INSERT INTO _uuid_migration_counts (table_name, row_count)
SELECT 'users', count(*) FROM users
UNION ALL SELECT 'teams', count(*) FROM teams
UNION ALL SELECT 'team_members', count(*) FROM team_members
UNION ALL SELECT 'correspondents', count(*) FROM correspondents
UNION ALL SELECT 'document_types', count(*) FROM document_types
UNION ALL SELECT 'documents', count(*) FROM documents
UNION ALL SELECT 'access_tokens', count(*) FROM access_tokens;

CREATE TEMPORARY TABLE _uuid_migration_users (
    old_id bigint PRIMARY KEY,
    new_id uuid NOT NULL UNIQUE
) ON COMMIT DROP;
CREATE TEMPORARY TABLE _uuid_migration_teams (
    old_id bigint PRIMARY KEY,
    new_id uuid NOT NULL UNIQUE
) ON COMMIT DROP;
CREATE TEMPORARY TABLE _uuid_migration_team_members (
    old_id bigint PRIMARY KEY,
    new_id uuid NOT NULL UNIQUE
) ON COMMIT DROP;
CREATE TEMPORARY TABLE _uuid_migration_correspondents (
    old_id bigint PRIMARY KEY,
    new_id uuid NOT NULL UNIQUE
) ON COMMIT DROP;
CREATE TEMPORARY TABLE _uuid_migration_document_types (
    old_id bigint PRIMARY KEY,
    new_id uuid NOT NULL UNIQUE
) ON COMMIT DROP;
CREATE TEMPORARY TABLE _uuid_migration_documents (
    old_id bigint PRIMARY KEY,
    new_id uuid NOT NULL UNIQUE
) ON COMMIT DROP;

INSERT INTO _uuid_migration_users SELECT id, gen_random_uuid() FROM users;
INSERT INTO _uuid_migration_teams SELECT id, gen_random_uuid() FROM teams;
INSERT INTO _uuid_migration_team_members SELECT id, gen_random_uuid() FROM team_members;
INSERT INTO _uuid_migration_correspondents SELECT id, gen_random_uuid() FROM correspondents;
INSERT INTO _uuid_migration_document_types SELECT id, gen_random_uuid() FROM document_types;
INSERT INTO _uuid_migration_documents SELECT id, gen_random_uuid() FROM documents;

DO $$
DECLARE
    expected record;
    mapping_count bigint;
BEGIN
    FOR expected IN SELECT table_name, row_count FROM _uuid_migration_counts LOOP
        IF expected.table_name = 'access_tokens' THEN
            CONTINUE;
        END IF;

        EXECUTE format('SELECT count(*) FROM _uuid_migration_%I', expected.table_name)
           INTO mapping_count;
        IF mapping_count <> expected.row_count THEN
            RAISE EXCEPTION 'UUID mapping count for % is %, expected %',
                expected.table_name, mapping_count, expected.row_count;
        END IF;
    END LOOP;
END
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM teams AS t
        LEFT JOIN _uuid_migration_users AS u ON u.old_id = t.owner_id
        WHERE u.old_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Orphan teams.owner_id prevents UUID migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM team_members AS tm
        LEFT JOIN _uuid_migration_teams AS t ON t.old_id = tm.team_id
        LEFT JOIN _uuid_migration_users AS u ON u.old_id = tm.user_id
        WHERE t.old_id IS NULL OR u.old_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Orphan team_members reference prevents UUID migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM documents AS d
        LEFT JOIN _uuid_migration_users AS u ON u.old_id = d.owner_id
        WHERE u.old_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Orphan documents.owner_id prevents UUID migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM documents AS d
        LEFT JOIN _uuid_migration_teams AS t ON t.old_id = d.team_id
        WHERE d.team_id IS NOT NULL AND t.old_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Orphan documents.team_id prevents UUID migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM documents AS d
        LEFT JOIN _uuid_migration_correspondents AS c ON c.old_id = d.correspondent_id
        WHERE d.correspondent_id IS NOT NULL AND c.old_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Orphan documents.correspondent_id prevents UUID migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM documents AS d
        LEFT JOIN _uuid_migration_document_types AS dt ON dt.old_id = d.document_type_id
        WHERE d.document_type_id IS NOT NULL AND dt.old_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Orphan documents.document_type_id prevents UUID migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM access_tokens AS token
        LEFT JOIN _uuid_migration_users AS u ON u.old_id = token.user_id
        WHERE u.old_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Orphan access_tokens.user_id prevents UUID migration';
    END IF;
END
$$;

CREATE TEMPORARY TABLE _uuid_migration_sequences (
    schema_name text NOT NULL,
    sequence_name text NOT NULL
) ON COMMIT DROP;

INSERT INTO _uuid_migration_sequences (schema_name, sequence_name)
SELECT sequence_schema.nspname, sequence_class.relname
  FROM (VALUES ('users'), ('teams'), ('team_members'), ('correspondents'), ('document_types'), ('documents'))
       AS source(table_name)
  CROSS JOIN LATERAL (
      SELECT pg_get_serial_sequence(format('%I.%I', current_schema(), source.table_name), 'id') AS sequence_name
  ) AS sequence_ref
  JOIN pg_class AS sequence_class ON sequence_class.oid = to_regclass(sequence_ref.sequence_name)
  JOIN pg_namespace AS sequence_schema ON sequence_schema.oid = sequence_class.relnamespace
 WHERE sequence_ref.sequence_name IS NOT NULL;

CREATE TEMPORARY TABLE _uuid_migration_converted_columns (
    relation_oid regclass NOT NULL,
    column_name text NOT NULL,
    PRIMARY KEY (relation_oid, column_name)
) ON COMMIT DROP;

INSERT INTO _uuid_migration_converted_columns (relation_oid, column_name)
SELECT target.relation_oid, converted.column_name
  FROM (VALUES
      ('users', 'id'),
      ('teams', 'id'), ('teams', 'owner_id'),
      ('team_members', 'id'), ('team_members', 'team_id'), ('team_members', 'user_id'),
      ('correspondents', 'id'),
      ('document_types', 'id'),
      ('documents', 'id'), ('documents', 'owner_id'), ('documents', 'team_id'),
      ('documents', 'correspondent_id'), ('documents', 'document_type_id'),
      ('access_tokens', 'user_id')
  ) AS converted(table_name, column_name)
  JOIN _uuid_migration_targets AS target USING (table_name);

CREATE TEMPORARY TABLE _uuid_migration_expected_foreign_keys (
    local_table text NOT NULL,
    local_column text NOT NULL,
    referenced_table text NOT NULL,
    referenced_column text NOT NULL,
    PRIMARY KEY (local_table, local_column, referenced_table, referenced_column)
) ON COMMIT DROP;

INSERT INTO _uuid_migration_expected_foreign_keys VALUES
    ('team_members', 'team_id', 'teams', 'id'),
    ('team_members', 'user_id', 'users', 'id'),
    ('documents', 'owner_id', 'users', 'id'),
    ('documents', 'team_id', 'teams', 'id'),
    ('documents', 'correspondent_id', 'correspondents', 'id'),
    ('documents', 'document_type_id', 'document_types', 'id'),
    ('access_tokens', 'user_id', 'users', 'id');

CREATE TEMPORARY TABLE _uuid_migration_expected_unique_constraints (
    table_name text NOT NULL,
    column_names text[] NOT NULL
) ON COMMIT DROP;

INSERT INTO _uuid_migration_expected_unique_constraints VALUES
    ('team_members', ARRAY['team_id', 'user_id']),
    ('access_tokens', ARRAY['user_id']);

-- Fail closed if the schema contains an affected FK or unique constraint that
-- this migration does not explicitly recreate, or is missing/has altered any
-- expected constraint. Constraint names may differ from Hibernate's names;
-- definitions and behavior must still match the current schema.
DO $$
DECLARE
    unexpected record;
    expected record;
    actual_count bigint;
BEGIN
    SELECT c.conrelid::regclass::text AS table_name, c.conname AS constraint_name
      INTO unexpected
      FROM pg_constraint AS c
     WHERE c.contype = 'f'
       AND EXISTS (
           SELECT 1
             FROM unnest(c.conkey) AS key(attnum)
             JOIN _uuid_migration_converted_columns AS converted
               ON converted.relation_oid = c.conrelid
             JOIN pg_attribute AS column_info
               ON column_info.attrelid = c.conrelid
              AND column_info.attnum = key.attnum
              AND column_info.attname::text = converted.column_name
       )
       AND NOT EXISTS (
           SELECT 1
             FROM _uuid_migration_expected_foreign_keys AS expected_fk
             JOIN _uuid_migration_targets AS local_target
               ON local_target.table_name = expected_fk.local_table
             JOIN _uuid_migration_targets AS referenced_target
               ON referenced_target.table_name = expected_fk.referenced_table
             JOIN pg_attribute AS local_column
               ON local_column.attrelid = local_target.relation_oid
              AND local_column.attname::text = expected_fk.local_column
             JOIN pg_attribute AS referenced_column
               ON referenced_column.attrelid = referenced_target.relation_oid
              AND referenced_column.attname::text = expected_fk.referenced_column
            WHERE c.conrelid = local_target.relation_oid
              AND c.confrelid = referenced_target.relation_oid
              AND c.conkey = ARRAY[local_column.attnum]
              AND c.confkey = ARRAY[referenced_column.attnum]
              AND c.confmatchtype = 's'
              AND c.confupdtype = 'a'
              AND c.confdeltype = 'a'
              AND c.condeferrable = false
              AND c.condeferred = false
              AND c.convalidated = true
       )
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'Unexpected affected foreign key %.%; refusing schema-drift migration',
            unexpected.table_name, unexpected.constraint_name;
    END IF;

    FOR expected IN SELECT * FROM _uuid_migration_expected_foreign_keys LOOP
        SELECT count(*)
          INTO actual_count
          FROM pg_constraint AS c
          JOIN _uuid_migration_targets AS local_target
            ON local_target.table_name = expected.local_table
           AND local_target.relation_oid = c.conrelid
          JOIN _uuid_migration_targets AS referenced_target
            ON referenced_target.table_name = expected.referenced_table
           AND referenced_target.relation_oid = c.confrelid
          JOIN pg_attribute AS local_column
            ON local_column.attrelid = local_target.relation_oid
           AND local_column.attname::text = expected.local_column
          JOIN pg_attribute AS referenced_column
            ON referenced_column.attrelid = referenced_target.relation_oid
           AND referenced_column.attname::text = expected.referenced_column
         WHERE c.contype = 'f'
           AND c.conkey = ARRAY[local_column.attnum]
           AND c.confkey = ARRAY[referenced_column.attnum]
           AND c.confmatchtype = 's'
           AND c.confupdtype = 'a'
           AND c.confdeltype = 'a'
           AND c.condeferrable = false
           AND c.condeferred = false
           AND c.convalidated = true;

        IF actual_count <> 1 THEN
            RAISE EXCEPTION 'Expected exactly one default FK %.% -> %.%, found %',
                expected.local_table, expected.local_column,
                expected.referenced_table, expected.referenced_column, actual_count;
        END IF;
    END LOOP;

    SELECT c.conrelid::regclass::text AS table_name, c.conname AS constraint_name
      INTO unexpected
      FROM pg_constraint AS c
      JOIN pg_index AS unique_index ON unique_index.indexrelid = c.conindid
     WHERE c.contype = 'u'
       AND EXISTS (
           SELECT 1
             FROM unnest(c.conkey) AS key(attnum)
             JOIN _uuid_migration_converted_columns AS converted
               ON converted.relation_oid = c.conrelid
             JOIN pg_attribute AS column_info
               ON column_info.attrelid = c.conrelid
              AND column_info.attnum = key.attnum
              AND column_info.attname::text = converted.column_name
       )
       AND NOT EXISTS (
           SELECT 1
             FROM _uuid_migration_expected_unique_constraints AS expected_unique
             JOIN _uuid_migration_targets AS target
               ON target.table_name = expected_unique.table_name
            WHERE c.conrelid = target.relation_oid
              AND c.conkey = ARRAY(
                  SELECT column_info.attnum
                    FROM unnest(expected_unique.column_names) WITH ORDINALITY AS expected_column(column_name, position)
                    JOIN pg_attribute AS column_info
                      ON column_info.attrelid = target.relation_oid
                     AND column_info.attname::text = expected_column.column_name
                   ORDER BY expected_column.position
              )
              AND c.condeferrable = false
              AND c.condeferred = false
              AND c.convalidated = true
              AND unique_index.indnullsnotdistinct = false
       )
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'Unexpected affected unique constraint %.%; refusing schema-drift migration',
            unexpected.table_name, unexpected.constraint_name;
    END IF;

    FOR expected IN SELECT * FROM _uuid_migration_expected_unique_constraints LOOP
        SELECT count(*)
          INTO actual_count
          FROM pg_constraint AS c
          JOIN pg_index AS unique_index ON unique_index.indexrelid = c.conindid
          JOIN _uuid_migration_targets AS target
            ON target.table_name = expected.table_name
           AND target.relation_oid = c.conrelid
         WHERE c.contype = 'u'
           AND c.conkey = ARRAY(
               SELECT column_info.attnum
                 FROM unnest(expected.column_names) WITH ORDINALITY AS expected_column(column_name, position)
                 JOIN pg_attribute AS column_info
                   ON column_info.attrelid = target.relation_oid
                  AND column_info.attname::text = expected_column.column_name
                ORDER BY expected_column.position
           )
           AND c.condeferrable = false
           AND c.condeferred = false
           AND c.convalidated = true
           AND unique_index.indnullsnotdistinct = false;

        IF actual_count <> 1 THEN
            RAISE EXCEPTION 'Expected exactly one default unique constraint on %.%, found %',
                expected.table_name, expected.column_names, actual_count;
        END IF;
    END LOOP;
END
$$;

-- Drop dependent constraints in dependency order: every affected FK first,
-- then affected unique constraints, then the converted primary keys. Checks
-- and constraints over untouched columns are left in place.
DO $$
DECLARE
    constraint_row record;
BEGIN
    FOR constraint_row IN
        SELECT c.conrelid::regclass AS table_oid, c.conname
          FROM pg_constraint AS c
         WHERE c.contype = 'f'
           AND EXISTS (
               SELECT 1
                 FROM unnest(c.conkey) AS key(attnum)
                 JOIN pg_attribute AS column_info
                   ON column_info.attrelid = c.conrelid
                  AND column_info.attnum = key.attnum
                 JOIN _uuid_migration_converted_columns AS converted
                   ON converted.relation_oid = c.conrelid
                  AND converted.column_name = column_info.attname::text
           )
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', constraint_row.table_oid, constraint_row.conname);
    END LOOP;

    FOR constraint_row IN
        SELECT c.conrelid::regclass AS table_oid, c.conname
          FROM pg_constraint AS c
         WHERE c.contype = 'u'
           AND EXISTS (
               SELECT 1
                 FROM unnest(c.conkey) AS key(attnum)
                 JOIN pg_attribute AS column_info
                   ON column_info.attrelid = c.conrelid
                  AND column_info.attnum = key.attnum
                 JOIN _uuid_migration_converted_columns AS converted
                   ON converted.relation_oid = c.conrelid
                  AND converted.column_name = column_info.attname::text
           )
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', constraint_row.table_oid, constraint_row.conname);
    END LOOP;

    FOR constraint_row IN
        SELECT c.conrelid::regclass AS table_oid, c.conname
          FROM pg_constraint AS c
         WHERE c.contype = 'p'
           AND EXISTS (
               SELECT 1
                 FROM unnest(c.conkey) AS key(attnum)
                 JOIN pg_attribute AS column_info
                   ON column_info.attrelid = c.conrelid
                  AND column_info.attnum = key.attnum
                 JOIN _uuid_migration_converted_columns AS converted
                   ON converted.relation_oid = c.conrelid
                  AND converted.column_name = column_info.attname::text
           )
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', constraint_row.table_oid, constraint_row.conname);
    END LOOP;
END
$$;

ALTER TABLE users ADD COLUMN _uuid_migration_id uuid;
ALTER TABLE teams ADD COLUMN _uuid_migration_id uuid;
ALTER TABLE team_members ADD COLUMN _uuid_migration_id uuid;
ALTER TABLE correspondents ADD COLUMN _uuid_migration_id uuid;
ALTER TABLE document_types ADD COLUMN _uuid_migration_id uuid;
ALTER TABLE documents ADD COLUMN _uuid_migration_id uuid;

ALTER TABLE teams ADD COLUMN _uuid_migration_owner_id uuid;
ALTER TABLE team_members ADD COLUMN _uuid_migration_team_id uuid;
ALTER TABLE team_members ADD COLUMN _uuid_migration_user_id uuid;
ALTER TABLE documents ADD COLUMN _uuid_migration_owner_id uuid;
ALTER TABLE documents ADD COLUMN _uuid_migration_team_id uuid;
ALTER TABLE documents ADD COLUMN _uuid_migration_correspondent_id uuid;
ALTER TABLE documents ADD COLUMN _uuid_migration_document_type_id uuid;
ALTER TABLE access_tokens ADD COLUMN _uuid_migration_user_id uuid;

UPDATE users AS u SET _uuid_migration_id = mapping.new_id
  FROM _uuid_migration_users AS mapping WHERE mapping.old_id = u.id;
UPDATE teams AS t SET _uuid_migration_id = mapping.new_id
  FROM _uuid_migration_teams AS mapping WHERE mapping.old_id = t.id;
UPDATE team_members AS tm SET _uuid_migration_id = mapping.new_id
  FROM _uuid_migration_team_members AS mapping WHERE mapping.old_id = tm.id;
UPDATE correspondents AS c SET _uuid_migration_id = mapping.new_id
  FROM _uuid_migration_correspondents AS mapping WHERE mapping.old_id = c.id;
UPDATE document_types AS dt SET _uuid_migration_id = mapping.new_id
  FROM _uuid_migration_document_types AS mapping WHERE mapping.old_id = dt.id;
UPDATE documents AS d SET _uuid_migration_id = mapping.new_id
  FROM _uuid_migration_documents AS mapping WHERE mapping.old_id = d.id;

UPDATE teams AS t SET _uuid_migration_owner_id = mapping.new_id
  FROM _uuid_migration_users AS mapping WHERE mapping.old_id = t.owner_id;
UPDATE team_members AS tm SET _uuid_migration_team_id = mapping.new_id
  FROM _uuid_migration_teams AS mapping WHERE mapping.old_id = tm.team_id;
UPDATE team_members AS tm SET _uuid_migration_user_id = mapping.new_id
  FROM _uuid_migration_users AS mapping WHERE mapping.old_id = tm.user_id;
UPDATE documents AS d SET _uuid_migration_owner_id = mapping.new_id
  FROM _uuid_migration_users AS mapping WHERE mapping.old_id = d.owner_id;
UPDATE documents AS d SET _uuid_migration_team_id = mapping.new_id
  FROM _uuid_migration_teams AS mapping WHERE mapping.old_id = d.team_id;
UPDATE documents AS d SET _uuid_migration_correspondent_id = mapping.new_id
  FROM _uuid_migration_correspondents AS mapping WHERE mapping.old_id = d.correspondent_id;
UPDATE documents AS d SET _uuid_migration_document_type_id = mapping.new_id
  FROM _uuid_migration_document_types AS mapping WHERE mapping.old_id = d.document_type_id;
UPDATE access_tokens AS token SET _uuid_migration_user_id = mapping.new_id
  FROM _uuid_migration_users AS mapping WHERE mapping.old_id = token.user_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE _uuid_migration_id IS NULL)
       OR EXISTS (SELECT 1 FROM teams WHERE _uuid_migration_id IS NULL OR _uuid_migration_owner_id IS NULL)
       OR EXISTS (SELECT 1 FROM team_members WHERE _uuid_migration_id IS NULL OR _uuid_migration_team_id IS NULL OR _uuid_migration_user_id IS NULL)
       OR EXISTS (SELECT 1 FROM correspondents WHERE _uuid_migration_id IS NULL)
       OR EXISTS (SELECT 1 FROM document_types WHERE _uuid_migration_id IS NULL)
       OR EXISTS (SELECT 1 FROM documents WHERE _uuid_migration_id IS NULL OR _uuid_migration_owner_id IS NULL)
       OR EXISTS (SELECT 1 FROM access_tokens WHERE _uuid_migration_user_id IS NULL) THEN
        RAISE EXCEPTION 'UUID migration left an unmapped required key';
    END IF;

    IF EXISTS (
        SELECT 1 FROM documents
        WHERE (team_id IS NOT NULL AND _uuid_migration_team_id IS NULL)
           OR (correspondent_id IS NOT NULL AND _uuid_migration_correspondent_id IS NULL)
           OR (document_type_id IS NOT NULL AND _uuid_migration_document_type_id IS NULL)
    ) THEN
        RAISE EXCEPTION 'UUID migration left an unmapped optional document reference';
    END IF;
END
$$;

ALTER TABLE users RENAME COLUMN id TO _uuid_migration_old_id;
ALTER TABLE users RENAME COLUMN _uuid_migration_id TO id;
ALTER TABLE users ALTER COLUMN id SET NOT NULL;
ALTER TABLE users DROP COLUMN _uuid_migration_old_id;

ALTER TABLE teams RENAME COLUMN id TO _uuid_migration_old_id;
ALTER TABLE teams RENAME COLUMN _uuid_migration_id TO id;
ALTER TABLE teams ALTER COLUMN id SET NOT NULL;
ALTER TABLE teams DROP COLUMN _uuid_migration_old_id;
ALTER TABLE teams RENAME COLUMN owner_id TO _uuid_migration_old_owner_id;
ALTER TABLE teams RENAME COLUMN _uuid_migration_owner_id TO owner_id;
ALTER TABLE teams ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE teams DROP COLUMN _uuid_migration_old_owner_id;

ALTER TABLE team_members RENAME COLUMN id TO _uuid_migration_old_id;
ALTER TABLE team_members RENAME COLUMN _uuid_migration_id TO id;
ALTER TABLE team_members ALTER COLUMN id SET NOT NULL;
ALTER TABLE team_members DROP COLUMN _uuid_migration_old_id;
ALTER TABLE team_members RENAME COLUMN team_id TO _uuid_migration_old_team_id;
ALTER TABLE team_members RENAME COLUMN _uuid_migration_team_id TO team_id;
ALTER TABLE team_members ALTER COLUMN team_id SET NOT NULL;
ALTER TABLE team_members DROP COLUMN _uuid_migration_old_team_id;
ALTER TABLE team_members RENAME COLUMN user_id TO _uuid_migration_old_user_id;
ALTER TABLE team_members RENAME COLUMN _uuid_migration_user_id TO user_id;
ALTER TABLE team_members ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE team_members DROP COLUMN _uuid_migration_old_user_id;

ALTER TABLE correspondents RENAME COLUMN id TO _uuid_migration_old_id;
ALTER TABLE correspondents RENAME COLUMN _uuid_migration_id TO id;
ALTER TABLE correspondents ALTER COLUMN id SET NOT NULL;
ALTER TABLE correspondents DROP COLUMN _uuid_migration_old_id;

ALTER TABLE document_types RENAME COLUMN id TO _uuid_migration_old_id;
ALTER TABLE document_types RENAME COLUMN _uuid_migration_id TO id;
ALTER TABLE document_types ALTER COLUMN id SET NOT NULL;
ALTER TABLE document_types DROP COLUMN _uuid_migration_old_id;

ALTER TABLE documents RENAME COLUMN id TO _uuid_migration_old_id;
ALTER TABLE documents RENAME COLUMN _uuid_migration_id TO id;
ALTER TABLE documents ALTER COLUMN id SET NOT NULL;
ALTER TABLE documents DROP COLUMN _uuid_migration_old_id;
ALTER TABLE documents RENAME COLUMN owner_id TO _uuid_migration_old_owner_id;
ALTER TABLE documents RENAME COLUMN _uuid_migration_owner_id TO owner_id;
ALTER TABLE documents ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE documents DROP COLUMN _uuid_migration_old_owner_id;
ALTER TABLE documents RENAME COLUMN team_id TO _uuid_migration_old_team_id;
ALTER TABLE documents RENAME COLUMN _uuid_migration_team_id TO team_id;
ALTER TABLE documents DROP COLUMN _uuid_migration_old_team_id;
ALTER TABLE documents RENAME COLUMN correspondent_id TO _uuid_migration_old_correspondent_id;
ALTER TABLE documents RENAME COLUMN _uuid_migration_correspondent_id TO correspondent_id;
ALTER TABLE documents DROP COLUMN _uuid_migration_old_correspondent_id;
ALTER TABLE documents RENAME COLUMN document_type_id TO _uuid_migration_old_document_type_id;
ALTER TABLE documents RENAME COLUMN _uuid_migration_document_type_id TO document_type_id;
ALTER TABLE documents DROP COLUMN _uuid_migration_old_document_type_id;

ALTER TABLE access_tokens RENAME COLUMN user_id TO _uuid_migration_old_user_id;
ALTER TABLE access_tokens RENAME COLUMN _uuid_migration_user_id TO user_id;
ALTER TABLE access_tokens ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE access_tokens DROP COLUMN _uuid_migration_old_user_id;

-- Dropping the old IDENTITY columns normally drops their owned sequences too.
-- Explicitly remove any sequence left behind by a legacy serial-based schema.
DO $$
DECLARE
    sequence_row record;
BEGIN
    FOR sequence_row IN SELECT schema_name, sequence_name FROM _uuid_migration_sequences LOOP
        EXECUTE format('DROP SEQUENCE IF EXISTS %I.%I', sequence_row.schema_name, sequence_row.sequence_name);
    END LOOP;
END
$$;

ALTER TABLE users ADD CONSTRAINT pk_users PRIMARY KEY (id);
ALTER TABLE teams ADD CONSTRAINT pk_teams PRIMARY KEY (id);
ALTER TABLE team_members ADD CONSTRAINT pk_team_members PRIMARY KEY (id);
ALTER TABLE correspondents ADD CONSTRAINT pk_correspondents PRIMARY KEY (id);
ALTER TABLE document_types ADD CONSTRAINT pk_document_types PRIMARY KEY (id);
ALTER TABLE documents ADD CONSTRAINT pk_documents PRIMARY KEY (id);

ALTER TABLE team_members ADD CONSTRAINT uq_team_members_team_user UNIQUE (team_id, user_id);
ALTER TABLE access_tokens ADD CONSTRAINT uq_access_tokens_user_id UNIQUE (user_id);

ALTER TABLE team_members ADD CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id);
ALTER TABLE team_members ADD CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE documents ADD CONSTRAINT fk_documents_owner FOREIGN KEY (owner_id) REFERENCES users (id);
ALTER TABLE documents ADD CONSTRAINT fk_documents_team FOREIGN KEY (team_id) REFERENCES teams (id);
ALTER TABLE documents ADD CONSTRAINT fk_documents_correspondent FOREIGN KEY (correspondent_id) REFERENCES correspondents (id);
ALTER TABLE documents ADD CONSTRAINT fk_documents_document_type FOREIGN KEY (document_type_id) REFERENCES document_types (id);
ALTER TABLE access_tokens ADD CONSTRAINT fk_access_tokens_user FOREIGN KEY (user_id) REFERENCES users (id);

DO $$
DECLARE
    index_row record;
BEGIN
    FOR index_row IN SELECT index_definition FROM _uuid_migration_indexes LOOP
        EXECUTE index_row.index_definition;
    END LOOP;
END
$$;

DO $$
DECLARE
    expected record;
    actual_count bigint;
BEGIN
    FOR expected IN SELECT table_name, row_count FROM _uuid_migration_counts LOOP
        EXECUTE format('SELECT count(*) FROM %I.%I', current_schema(), expected.table_name)
           INTO actual_count;
        IF actual_count <> expected.row_count THEN
            RAISE EXCEPTION 'Row count changed for % during UUID migration: got %, expected %',
                expected.table_name, actual_count, expected.row_count;
        END IF;
    END LOOP;
END
$$;
