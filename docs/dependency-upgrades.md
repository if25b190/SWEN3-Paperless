# Dependency update — 2026-11-15

Versions were checked against npm, Maven Central, the Gradle release API, GitHub
releases, and published container manifests. Prereleases and unpublished artifact
entries are excluded. Spring Boot continues to manage its tested transitive stack;
Kotlin libraries are aligned with the Kotlin Gradle plugin.

| Component | Previous | Updated |
| --- | --- | --- |
| Kotlin plugins | 2.3.21 | 2.4.21 |
| Spring Boot | 4.1.1 | 4.1.2 |
| Gradle wrappers | 9.7.1 | 9.9.1 |
| Temurin container (Java 25 LTS) | 25 (floating) | 25.0.5_8 |
| OpenAPI Generator | 7.11.0 | 7.14.0 |
| AWS SDK BOM | 2.57.3 | 2.59.1 |
| springdoc-openapi | 3.1.1 | 3.2.0 |
| jackson-databind-nullable | 0.2.6 | 0.2.13 |
| ktlint | Spotless default | 1.8.0 (explicit) |
| Node container | 22 | 26.13.1 |
| pnpm | 12.4.2 | 12.21.0 |
| Next.js and eslint-config-next | 16.3.5 | 16.4.6 |
| React and React DOM | 19.2.8 | 19.3.1 |
| TypeScript compiler | 5.9.3 | 7.0.2 |
| ESLint | 9.x | 10.15.0 |
| Vitest | 5.0.5 | 5.1.0 |
| jsdom | 30.1.3 | 31.0.1 |
| Tailwind CSS and PostCSS plugin | 4.3.3 | 4.3.5 |
| Lucide React | 1.52.0 | 1.73.0 |
| Elastic stack images | 9.5.5 | 9.5.6 |
| Grafana | 12.0.0 | 13.3.1 |
| Tempo | 2.8.1 | 3.1.1 |
| PostgreSQL | 16 | 18.7 |
| RustFS | 1.0.4 | 1.0.5 |
| AWS CLI | 2.34.0 | 2.40.3 |
| curl bootstrap image | 8.12.1 | 8.23.0 |
| actions/checkout | v4 | v7.0.1 |
| actions/setup-java | v4 | v6.3.0 |
| actions/upload-artifact | v4 | v7.0.2 |

Radix UI packages, React/Node type definitions, and the Vite React plugin are also
updated; their exact versions are in `paperless-ui/package.json` and the regenerated
pnpm lockfile. Java remains on the project's Java 25 LTS baseline. MapStruct 1.6.3,
Spotless 8.11.0, Swagger annotations 2.2.55, Spring dependency management 1.1.7,
RabbitMQ 4.4.0, OpenTelemetry agent 2.33.1, and collector 0.165.0 were already current.

## TypeScript compatibility

TypeScript 7 does not provide the JavaScript compiler API required by Next.js and
typescript-eslint. The package follows [Microsoft's side-by-side migration](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/#running-side-by-side-with-typescript-6-0):
`@typescript/native` aliases TypeScript 7.0.2 and supplies `tsc`, while `typescript`
aliases `@typescript/typescript6` 6.0.2 for API consumers. `pnpm typecheck` runs the
native compiler; Next.js and ESLint use the compatibility API.

Some ESLint plugins still declare peer ranges ending at ESLint 9. Next.js 16.4
supports ESLint 10, and linting is verified without suppressing those peer warnings.

## Persistent data and deployment

These source updates do not upgrade running services or rewrite existing volumes.
PostgreSQL 16 data cannot be mounted directly into PostgreSQL 18. Back up the old
cluster and use a supported dump/restore or `pg_upgrade` process before switching.
Restore into a separate volume and retain the original until the migration has
been verified. The explicit `PGDATA=/var/lib/postgresql/data` preserves the old
mount location and prevents an existing 16 cluster from being silently bypassed
by PostgreSQL 18's new default directory layout. Fresh installations use that same
explicit path.

Tempo 3 is configured in monolithic mode without Kafka. Its live-store replaces
the old ingester, and the compactor block is replaced by per-tenant retention under
`overrides.defaults.compaction`. Keep a backup and flush the old deployment's
pending traces before upgrading; persisted Tempo 2 trace blocks remain readable.
The Tempo 3 image has no shell or HTTP client, so readiness is checked externally
by `infra/observability/verify.py` rather than an unavailable `wget` healthcheck.

Existing Elasticsearch 8.17 data still requires the supported upgrade through
8.19 before 9.5.6. Preserve the existing data and snapshots; do not remove volumes
to bypass a version mismatch. Elastic's artifact API lists future versions too;
9.5.6 was selected from the [published container registry](https://www.docker.elastic.co/r/elasticsearch)
and all four Elastic image manifests were checked.

## Verification

- Backend: full 151-test suite completed with two opt-in tests skipped; the S3
  integration test subsequently passed against RustFS 1.0.5 and AWS SDK 2.59.1.
- Worker: full 36-test suite completed against live RabbitMQ and Elasticsearch
  9.5.6, with only five tests requiring the optional local OCR toolchain skipped.
  All six RabbitMQ integration tests also passed with the OpenTelemetry agent.
- UI: lint, seven unit tests, native TypeScript typechecking, and the production
  build passed; login and registration rendered without browser/hydration errors.
- Fresh PostgreSQL 18.7: Flyway migrations, registration/login, and document
  upload/download/delete passed, including an exact S3 byte roundtrip.
- Fresh Elasticsearch/Logstash 9.5.6, Grafana 13.3.1, and Tempo 3.1.1: readiness,
  ingest parsing, datasource provisioning, TraceQL search, and real HTTP/RabbitMQ
  log-to-trace lookup passed. The worker JSON file was ingested by Logstash.
- Both Gradle wrappers match; Compose configuration and whitespace checks pass.

Existing application data volumes were not migrated or removed. Container checks
used a separate Compose project with fresh volumes.

## Executed data migrations — 2026-11-15 (local stack, project `swen3-paperless`)

- **PostgreSQL 16.15 → 18.7** via dump/restore: `pg_dump` of the `paperless`
  database (plus `pg_dumpall --globals-only`) taken from the running 16 container
  into `/tmp/paperless-migration/`. The old cluster was copied to the retained
  volume `swen3-paperless_postgres-data-pg16-backup`, the compose volume
  `swen3-paperless_postgres-data` was recreated empty, and the dump was restored
  into the fresh 18.7 cluster with `ON_ERROR_STOP=1`. Row counts verified equal
  (4 users, 2 teams, 4 members, 3 documents, 2 document types, 4 tokens, 4
  Flyway rows); backend registered/logged in/listed users against 18.7 and Flyway
  validated the restored history. Delete `swen3-paperless_postgres-data-pg16-backup`
  and `/tmp/paperless-migration/` once satisfied.
- **Elasticsearch 8.17.0 → 9.5.6** in-place on `swen3-paperless_elasticsearch-data`:
  8.17.0 → 8.19.8 (temporary container, waited for green) → 9.5.6 via Compose.
  The volume held no user indices; the `paperless-container-logs` pipeline and
  index template were re-applied by `elasticsearch-bootstrap`, and Logstash
  resumed ingesting worker logs into 9.5.6 (verified via
  `infra/observability/verify.py`: Tempo readiness, log parsing, Grafana
  datasources/dashboard/TraceQL all PASS).
