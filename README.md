# SWEN3 - Paperless

### Philip Duong & Patrick Hornek

A document management and processing platform consisting of a Spring Boot (Kotlin) backend and a Next.js (TypeScript) web interface, backed by PostgreSQL.

---

## Getting Started Locally

### Prerequisites

- **[Docker](https://docs.docker.com/get-docker/)** & **[Docker Compose](https://docs.docker.com/compose/)** (v2.24+)

*(Optional for running services natively without Docker: Java 25+, Gradle, Node.js 22+, and pnpm 12+)*

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
- **`ui`**: Next.js frontend application
- **`db`**: PostgreSQL 16 database
- **`rabbitmq`**: RabbitMQ broker with the management UI
- **`rustfs`**: Persistent S3-compatible document storage; `rustfs-init` creates the private bucket
- **`ocr-worker`**: RabbitMQ consumer that fetches documents from RustFS and extracts text with Tesseract

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

---

### Authentication

Public registration (`POST /api/v1/users`) does not grant access to other users' documents. Documents are private to their uploader unless assigned to a team, where access follows current membership. See [Team and document access](docs/team-document-access.md) for roles, sharing, and existing-database compatibility. Login returns an opaque bearer token and replaces the account's previous session. Existing development accounts with plaintext passwords must be recreated before they can log in.

### Application Logging

Application logs default to `INFO`; set `PAPERLESS_LOG_LEVEL=DEBUG` in `.env.local`
for request timing and search diagnostics. Each request returns an `X-Request-ID`
that also appears in its application logs. See [Logging](docs/logging.md) for
the recorded events and privacy rules.

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
and deleting the pending job can produce duplicates. The standalone Python
`ocr-worker` fetches the exact `storageKey` from RustFS and runs Tesseract on PDF
pages and images (English and German by default). UTF-8 plain text is also supported.
It saves a checkpoint at `ocr-results/<jobId>.json` before publishing a confirmed
result to `paperless.ocr.results`. The backend applies the text to `ocr_content`
and sets `COMPLETED`, or sets `FAILED` for permanently failed jobs. The result
handler ignores deleted documents and duplicate results without changing sharing.

The worker acknowledges only after its output is durable and the result event is
confirmed. Redelivery reuses the checkpoint, avoiding repeated OCR. Transient
failures retry through `paperless.ocr.retry` after ten seconds, up to three total
attempts. Invalid input and exhausted jobs go to `paperless.ocr.failed`; operational
failure messages never include extracted text or credentials. The worker runs
separately from the backend and keeps AMQP heartbeats active during OCR.
See [worker setup and tests](paperless-worker/README.md).

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
