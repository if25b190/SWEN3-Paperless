# SWEN3 - Paperless

### Philip Duong & Patrick Hornek

A document management and processing platform consisting of a Spring Boot (Kotlin) backend, a separate OCR worker (Ghostscript + Tesseract), and a Next.js (TypeScript) web interface, backed by PostgreSQL and RabbitMQ.

---

## Getting Started Locally

### Prerequisites

- **[Docker](https://docs.docker.com/get-docker/)** & **[Docker Compose](https://docs.docker.com/compose/)** (v2.24+)

*(Optional for running services natively without Docker: Java 25 LTS, Gradle 9.9.1, Node.js 26.13.1+, and pnpm 12.21.0)*

---

### 1. Configure Environment Variables

Create your local environment configuration file from the template:

```bash
cp .env.example .env.local
```

Adjust any values (such as credentials, ports, or passwords) in `.env.local` as needed. All services in Docker Compose read from this file.

---

### 2. Start the Stack with Docker Compose

Build and launch all containers in detached mode:

```bash
docker compose --env-file .env.local up --build -d
```

This starts:
- **`backend`**: Spring Boot REST API
- **`worker`**: OCR worker — consumes OCR jobs from RabbitMQ and extracts text with Ghostscript + Tesseract
- **`ui`**: Next.js frontend application
- **`db`**: PostgreSQL 18 database
- **`rabbitmq`**: RabbitMQ broker with the management UI
- **`rustfs`**: Persistent S3-compatible document storage; `rustfs-init` creates the private bucket
- **`elasticsearch`**: Elasticsearch — the OCR document search index and the log store
- **`kibana`**: Kibana UI for browsing the search index and the logs
- **`elastic-agent`**: [Elastic Agent](https://www.elastic.co/what-is/elastic-agent) — tails every service's container logs and ships them to Elasticsearch
- **`apm-server`**: [Elastic APM Server](https://www.elastic.co/guide/en/apm) — receives the OTLP traces exported by the backend and worker and indexes them into Elasticsearch

---

### 3. Service Endpoints

Once all services are healthy and running:

| Service | URL | Description |
| :--- | :--- | :--- |
| **Frontend UI** | [http://localhost:3000](http://localhost:3000) | Next.js user interface |
| **Backend REST API** | [http://localhost:8080/api/v1](http://localhost:8080/api/v1) | Spring Boot backend |
| **Swagger UI** | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | Interactive OpenAPI documentation |
| **OpenAPI v3 Spec** | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) | Raw OpenAPI JSON definition |
| **PostgreSQL** | `localhost:5432` | Relational database (user/pass in `.env.local`) |
| **RabbitMQ Management** | [http://localhost:15672](http://localhost:15672) | Queues and connections (credentials in `.env.local`) |
| **RustFS Console** | [http://localhost:9001](http://localhost:9001) | Object storage (credentials in `.env.local`) |
| **Kibana** | [http://localhost:5601](http://localhost:5601) | Search index, logs, and traces UI |

---

### Authentication

Public registration (`POST /api/v1/users`) does not grant access to other users' documents. Documents are private to their uploader unless assigned to a team, where access follows current membership. See [Team and document access](docs/team-document-access.md) for roles, sharing, and existing-database compatibility. Login returns an opaque bearer token and replaces the account's previous session. Existing development accounts with plaintext passwords must be recreated before they can log in.

### Application Logging

Application logs default to `INFO`; set `PAPERLESS_LOG_LEVEL=DEBUG` in `.env.local`
for request timing and search diagnostics. Each request returns an `X-Request-ID`
that also appears in its application logs. The applications additionally export
distributed traces (OpenTelemetry): logs inside an active span carry a `trace_id` that
selects the corresponding trace in [Kibana](#4--viewing-logs--status). See [Logging](docs/logging.md) for the recorded events and privacy
rules.

### OCR Messaging

Each upload saves a pending OCR job alongside the document in one database
transaction. A background dispatcher publishes it to RabbitMQ and removes the
pending job only after the broker confirms delivery to a queue. If RabbitMQ is
unavailable, uploads still succeed and pending jobs retry every second by default.
Rolled-back uploads never publish OCR requests. Uploaded files are stored in the
private RustFS bucket configured by `S3_BUCKET`, persisted in `rustfs-data`.
Upload, download, and deletion use the same `documents/...` object key sent to
the OCR queue. Authorization still runs in the backend before accessing storage.

The backend defaults to S3 storage. Native runs use `S3_ENDPOINT` (default
`http://localhost:9000`); Compose uses `http://rustfs:9000`. Set `RUSTFS_ACCESS_KEY`,
`RUSTFS_SECRET_KEY`, `S3_BUCKET`, and `S3_REGION` in `.env.local`. The bucket is
created before the backend starts and is never made public. Rollbacks remove
newly uploaded objects; document deletion removes the object after database commit.

For an existing installation, stop uploads, set `MIGRATE_LEGACY_DOCUMENTS=true`
for one Compose startup, and reset it after `rustfs-init` completes. This copies
the old `document-files` volume into the bucket under the existing object keys.
The original volume remains intact. Restarting the stack normally does not copy
legacy files. Tests use `DOCUMENT_STORAGE_TYPE=local` and require no storage server.

To verify the S3 adapter against a running RustFS instance, run from `paperless-backend`:

```bash
PAPERLESS_S3_TEST=true ./gradlew test --tests '*S3DocumentStorageIntegrationTest'
```

The durable exchange `paperless.ocr` routes `document.uploaded` to
`paperless.ocr.requests`. Messages contain JSON fields `jobId`, `documentId`,
`storageKey`, and `contentType`; the AMQP message ID is the stable `jobId`.
Delivery is at least once: a confirmation timeout or a crash between publishing
and deleting the pending job can produce duplicates.

The same exchange routes `document.deleted` to `paperless.ocr.deletions`: after
a document deletion commits, the backend publishes a JSON `DocumentDeletion`
message (field `documentId`; the AMQP message ID is the document ID) and the
worker removes the document's entry from the Elasticsearch index. Like the
post-commit file deletion, a failed publish is logged and swallowed, so a
broker outage during deletion can leave the index entry behind.

OCR processing runs in the separate **`worker`** application (`paperless-worker`),
which consumes `paperless.ocr.requests`. Its container image bundles **Ghostscript**
and **Tesseract** (English by default). For each job the worker reads the file named
by `storageKey` from the shared `document-files` volume, rasterizes the PDF pages with
Ghostscript, and extracts text with Tesseract. Idempotency comes from a claim step:
the document is moved `PENDING → OCR_IN_PROGRESS`, so a duplicate job for an already
claimed document is skipped rather than reprocessed. On success the worker indexes the
extracted text in **Elasticsearch** — one document per `documentId` in the
`paperless-documents` index (configurable via `OCR_INDEX_NAME`, with fields `id`,
`content`, and `indexedAt`; the index is created at startup if missing) — and stores
the text as the document's `ocr_content` and sets its status to `COMPLETED`
(configurable via `OCR_FINAL_STATUS`). Indexing happens before the document is
completed, so a `COMPLETED` document always has a search entry; if the index run
fails, the document is set to `FAILED` and the message is retried and reindexed.
If the document is deleted while OCR is running, the worker removes the freshly
created index entry instead of completing the document; already indexed
documents are cleaned up by the `document.deleted` message.
On any other failure the worker sets `FAILED` and rethrows so the message is retried
and then routed to the durable dead-letter queue `paperless.ocr.failed` through
`paperless.ocr.dead-letter`. The compose stack runs a single-node Elasticsearch
(`elasticsearch` service, security disabled) that the worker connects to via
`ELASTICSEARCH_HOST`, `ELASTICSEARCH_PORT`, `ELASTIC_USER`, and `ELASTIC_PASSWORD`.

The same Elasticsearch instance also stores the worker's log events. The worker
appends every log event as a JSON line to the file configured by
`WORKER_LOG_FILE` (optional for native runs); in the compose
stack that file lives on the shared `worker-logs` volume, which the `logstash`
service tails and writes to the `paperless-worker-logs-*` index (pipeline in
`infra/logstash/`). The `kibana` service (bound to
`127.0.0.1:${KIBANA_PORT:-5601}`) provides the UI for both indices.

The **`elastic-agent`** service (a standalone
[Elastic Agent](https://www.elastic.co/what-is/elastic-agent)) collects a second
class of logs: it tails the container logs of every service in the stack from the
Docker log directory and ships them to Elasticsearch as the
`paperless-container-logs` index. Before indexing, the
`paperless-container-logs` [ingest pipeline](https://www.elastic.co/docs/reference/enrich-pipelines/ingest-pipelines)
transforms each event. The pipeline is loaded at startup by the one-shot
`elasticsearch-bootstrap` service (definition in
`infra/elasticsearch/pipelines/`); it parses each Spring
Boot log line into
structured ECS fields — `log.level`, `log.logger`, `log.thread.name`,
`http.request.id`, and `trace.id` — and tags lines that don't match the Spring
Boot format with `paperless-log-unparsed`.

The **`apm-server`** service provides distributed tracing
(OpenTelemetry/APM). Both the backend and the worker create spans for HTTP
requests, database access, and RabbitMQ publish/consume and export them as OTLP
to the APM server's intake (`OTEL_EXPORTER_OTLP_ENDPOINT`, default
`http://localhost:8200`). The W3C `traceparent` context travels with the OCR
message in the AMQP headers, so the worker's processing joins its publisher's
trace. The original upload HTTP trace is separate because the database outbox
dispatches later without retaining trace context. Use `jobId` and `documentId`
to correlate upload and dispatch. The APM server indexes traces into
**Elasticsearch** as the `traces-apm.*` data streams. Logs inside an active span
carry the W3C trace ID: `trace_id` in console output, and `trace.id` in worker
ECS JSON. Container ingestion also normalizes it to `trace.id`; startup logs
have no active trace. Browse the traces in Kibana's *Observability → APM* app
(http://localhost:5601), or alongside the logs in *Analytics → Discover* with
the `traces-apm.*` data view.

Worker behaviour is configured with `WORKER_ENABLED`, `OCR_TESSERACT_LANGUAGES`,
`OCR_DPI`, `OCR_PROCESS_TIMEOUT`, `OCR_FINAL_STATUS`,
`ELASTICSEARCH_HOST`, `ELASTICSEARCH_PORT`, `ELASTIC_USER`, `ELASTIC_PASSWORD`,
`OCR_INDEX_NAME`, `WORKER_LOG_FILE`, and `OTEL_EXPORTER_OTLP_ENDPOINT`.

For a native backend, configure `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`,
and `RABBITMQ_PASS`; Docker Compose supplies the broker hostname automatically.
`OCR_DISPATCH_ENABLED` controls background publishing and `OCR_DISPATCH_DELAY`
sets its polling delay in milliseconds (default `1000`). The publisher waits up
to five seconds for confirmation. Exchange, queue, routing key, and confirmation
timeout can also be overridden through `paperless.ocr.*` Spring properties.

The regular backend test suite runs without RabbitMQ. To run the isolated live
broker check as well, from `paperless-backend` with broker credentials configured:

```bash
PAPERLESS_RABBITMQ_TEST=true ./gradlew test --tests '*RabbitOcrMessagePublisherIntegrationTest'
```

---

### 4. Viewing Logs & Status

Check running container status:
```bash
docker compose --env-file .env.local ps
```

Follow container logs:
```bash
docker compose --env-file .env.local logs -f
```

Browse the worker's log events, the document search index, and the container logs
in Kibana (http://localhost:5601 by default, bound to localhost only): in
*Management → Stack Management → Data Views*, create a view for the
`paperless-worker-logs-*`, `paperless-documents`, or `paperless-container-logs`
index, then open *Analytics → Discover*. In the `paperless-container-logs` view you
can filter on the parsed `log.level` and `log.logger` fields.

Browse traces in the same Kibana UI: open *Observability → APM* for the trace
waterfall views, or open *Analytics → Discover* with the `traces-apm.*` data
view and filter by a log entry's `trace_id` to see that trace's indexed span
documents.

---

### 5. Stopping the Stack

Stop all running containers:
```bash
docker compose --env-file .env.local down
```

To stop and remove all volumes (including database data):
```bash
docker compose --env-file .env.local down -v
```

The REST API uses the `/api/v1` path prefix. See [API versioning](docs/api-versioning.md) for the version policy.
