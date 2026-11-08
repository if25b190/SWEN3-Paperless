# SWEN3 - Paperless

### Philip Duong & Patrick Hornek

A document management and processing platform consisting of a Spring Boot (Kotlin) backend, a separate OCR worker (Ghostscript + Tesseract), and a Next.js (TypeScript) web interface, backed by PostgreSQL and RabbitMQ.

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
- **`worker`**: OCR worker — consumes OCR jobs from RabbitMQ and extracts text with Ghostscript + Tesseract
- **`ui`**: Next.js frontend application
- **`db`**: PostgreSQL 16 database
- **`rabbitmq`**: RabbitMQ broker with the management UI

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
Rolled-back uploads never publish OCR requests. Docker stores uploaded files in
the `document-files` volume.

The durable exchange `paperless.ocr` routes `document.uploaded` to
`paperless.ocr.requests`. Messages contain JSON fields `jobId`, `documentId`,
`storageKey`, and `contentType`; the AMQP message ID is the stable `jobId`.
Delivery is at least once: a confirmation timeout or a crash between publishing
and deleting the pending job can produce duplicates.

OCR processing runs in the separate **`worker`** application (`paperless-worker`),
which consumes `paperless.ocr.requests`. Its container image bundles **Ghostscript**
and **Tesseract** (English by default). For each job the worker reads the file named
by `storageKey` from the shared `document-files` volume, rasterizes the PDF pages with
Ghostscript, and extracts text with Tesseract. Idempotency comes from a claim step:
the document is moved `PENDING → OCR_IN_PROGRESS`, so a duplicate job for an already
claimed document is skipped rather than reprocessed. On success the worker stores the
extracted text as the document's `ocr_content` and sets its status to `COMPLETED`
(configurable via `OCR_FINAL_STATUS`); on failure it sets `FAILED` and rethrows so the
message is retried and then routed to the durable dead-letter queue `paperless.ocr.failed`
through `paperless.ocr.dead-letter`.

The backend's built-in `OcrWorker` is an empty listener scaffold kept disabled
(`OCR_WORKER_ENABLED=false`) because real processing now happens in the worker
application. Worker behaviour is configured with `WORKER_ENABLED`,
`OCR_TESSERACT_LANGUAGES`, `OCR_DPI`, `OCR_PROCESS_TIMEOUT`, and `OCR_FINAL_STATUS`.

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
