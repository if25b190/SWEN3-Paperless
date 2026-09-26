# Database migrations

Production migrations are under `postgresql/` and use the PostgreSQL-specific
Flyway location configured in the application. A new, empty PostgreSQL database
runs `V1__legacy_hibernate_schema.sql` to create the legacy BIGINT/identity
schema, then `V2__convert_primary_keys_to_uuid.sql` to migrate it to native UUID
keys, and `V3__remove_correspondents.sql` to remove correspondent metadata.
The UUID conversion relies on PostgreSQL 16's built-in `gen_random_uuid()`.

V3 drops the `documents.correspondent_id` foreign key and column, then drops the
`correspondents` table. PostgreSQL applies this Flyway migration transactionally.
The change is safe whether documents have a null or non-null correspondent ID:
all correspondent associations and correspondent records are intentionally
discarded. This data loss is irreversible without restoring a backup; make and
verify a backup before applying V3 if the data may be needed.

## Existing Hibernate-managed databases

Do **not** enable `baselineOnMigrate` or baseline an unknown database. Flyway's
baseline operation records a version; it does not run V1 or verify the schema.
Before baselining an existing installation:

1. Stop the application and all other writers, and make/verify a database
   backup.
2. Inspect the existing schema and compare it with V1: all tables and columns,
   SQL types/lengths/nullability, primary keys, unique constraints, foreign
   keys, and indexes must match. The schema must include `access_tokens` and
   its legacy BIGINT `user_id`. Do not baseline a missing, partially upgraded,
   or otherwise different schema; resolve the drift first.
3. Explicitly baseline the verified schema at version 1, using the same
   database URL, credentials, and schema as the application. From the
   repository root, a Flyway CLI invocation is:

   ```sh
   flyway \
     -url=jdbc:postgresql://HOST:5432/paperless \
     -user=paperless \
     -schemas=public \
     -locations=filesystem:paperless-backend/src/main/resources/db/migration/postgresql \
     -baselineVersion=1 \
     -baselineDescription="verified legacy Hibernate schema" \
     baseline
   ```

After this explicit baseline, deploying the application applies V2 and V3; V1
is recorded as the baseline and is not executed. V2 is transactional and takes
exclusive locks on the affected tables, so keep other application instances
and database writers stopped until migration completion is confirmed. The
migration recreates primary/foreign/unique constraints (including their backing
indexes) and saves/recreates standalone indexes on the migrated tables.

## H2 tests

Tests retain their existing H2 Hibernate `ddl-auto: create-drop` schema
lifecycle. Flyway is disabled for the test profile, so PostgreSQL migrations
are not run against H2.
