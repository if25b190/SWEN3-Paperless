# Logging

## Logger Setup

- Instantiate the SLF4J logger using `LoggerFactory.getLogger(...)`:
  ```kotlin
  companion object {
      private val log = LoggerFactory.getLogger(FeatureServiceImpl::class.java)
  }
  ```
  or as a private property on the class:
  ```kotlin
  private val log = LoggerFactory.getLogger(javaClass)
  ```

## Log Levels

- `ERROR`: Unrecoverable errors and unexpected exceptions.
- `WARN`: Recoverable unexpected states, client-side anomalies, or transient errors.
- `INFO`: Business-relevant milestones and auditable events (e.g., entity created, batch job finished).
- `DEBUG` / `TRACE`: Diagnostic messages for local testing and debugging.

## Message Format & Placeholders

Keep log formats uniform across the entire codebase:

```kotlin
log.info("[<FEATURE>] - ACTION: <method>: <field>: {}", value)
```

- **Use `{}` placeholders:** Always use parameter placeholders. Never use string concatenation, which incurs performance overhead and risks leaking sensitive data.
- **Traceability:** Include identifiers that facilitate end-to-end tracing across requests (e.g., request ID, user ID).

## Hygiene & Error Boundary

- **No sensitive data:** Never log credentials, tokens, passwords, or personal identifiable information (PII).
- **Service layer logging:** Keep logging primarily within the service and application layer.
- **No double logging on exceptions:** Code throwing a domain exception must not log it before throwing. The `GlobalExceptionHandler` logs the exception once at the boundary.

## Runtime Configuration

`PAPERLESS_LOG_LEVEL` controls the application package's level and defaults to
`INFO`. Set it to `DEBUG` for HTTP status/duration and search result counts.
Every request receives a server-generated `X-Request-ID`. The same value is
available as `requestId` in SLF4J MDC and appears in the default console pattern.
The filter runs before authentication and clears its MDC value after the request,
including failed requests. Client-provided request IDs are not trusted.

## Distributed Tracing

Both applications run the OpenTelemetry Java agent
(`-javaagent:/opt/otel/opentelemetry-javaagent.jar`, see the Dockerfiles):
spans are created for HTTP requests, database access, and RabbitMQ
publish/consume and exported as OTLP to the collector configured by
`OTEL_EXPORTER_OTLP_ENDPOINT` (default `http://localhost:4317`). The W3C
`traceparent` context is propagated with OCR messages, so the worker's
processing joins the publisher/dispatch trace. Uploads first commit a database outbox
job; that outbox does not store W3C context. The later scheduled dispatch starts a
separate trace from the original upload HTTP request. Correlate those operations
with `jobId` and `documentId` rather than assuming a shared trace ID.

The agent injects `trace_id` and `span_id` into each Logback event's MDC map;
it does not set the SLF4J thread-local MDC, so application code must not rely on
`MDC.get("trace_id")`. Console patterns include `trace_id`; worker ECS JSON logs
rename it to `trace.id` and `span_id` to `span.id`. Logstash normalizes those
fields to ECS objects before indexing. Console ingestion also stores `trace.id`.
`trace_id=none` marks logs outside an active span (for example startup) and is
excluded from trace correlation. These lines legitimately have no indexed trace.
Trace lookup also requires sampling to retain the span; the collector indexes
the retained spans into Elasticsearch's `paperless-traces` index, browsable in
Kibana.

Services record login, resource creation/update/deletion, and membership changes
with resource IDs. These are operational milestones within a transaction, not a
durable audit trail or proof of transaction commit. Client errors are logged once
at the exception handler or security boundary. Unexpected exceptions are logged
at `ERROR` with their type and stack frames; exception messages are omitted
because they may contain request data.

Request URLs, query text, request/response bodies, usernames, credentials, bearer
tokens, document titles, filenames, and content are not logged by the application.

## Worker logs in Elasticsearch and Kibana

Compose sets `WORKER_LOG_FILE=/var/log/paperless-worker/worker.json`. The worker writes
one ECS JSON event per line to the `worker-logs` volume, which Logstash mounts read-only.
The worker image creates the log directory with ownership for its non-root application user.
For native runs, set `WORKER_LOG_FILE` to a writable path to enable file logging.

Log files rotate at 10 MB with seven days of history and a 100 MB archive limit.
Archives remain uncompressed so Logstash can catch up after downtime or rotation.
Logstash persists file offsets and its event queue in `logstash-data` and sends events to
`paperless-worker-logs-YYYY.MM.dd`. Each original JSON line is hashed for its Elasticsearch
document ID, so replay after restart or rotation updates the same event instead of creating
duplicates. OCR content continues to use the separate
`paperless-documents` index.

Start the stack with `docker compose up -d --build`. Open Kibana at
`http://localhost:5601` (or `KIBANA_PORT`), create a data view for
`paperless-worker-logs-*` with `@timestamp` as the time field, and open Discover.
Useful fields include `service.name`, `log.level`, `log.logger`, and `message`.

Check the ingestion services with `docker compose ps elasticsearch logstash kibana`.
Inspect pipeline errors with `docker compose logs logstash`. The Logstash healthcheck
requires the `main` pipeline to be running. Elasticsearch security is disabled in this
local Compose setup, so the pipeline uses the internal HTTP endpoint without credentials.

## Trace correlation and verification

Logs and traces share one Elasticsearch instance. Logs are indexed as described
above; the OpenTelemetry collector indexes spans into the `paperless-traces` index
with a `trace.id` field. To find a trace from a log entry, filter the
`paperless-traces-*` data view in Kibana's Discover by the log's trace ID. The
reverse lookup works the same way: filter the log data views by a trace document's
`trace.id` to list its log events.

For native applications, attach the same Java agent as the Docker images and set
`OTEL_EXPORTER_OTLP_PROTOCOL=grpc`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317`,
`OTEL_SERVICE_NAME=paperless` (or `paperless-worker`), and disable metrics/log export.
Compose pins the exporter to the collector's internal address; a native localhost
endpoint in `.env.local` must not redirect a container to itself.

Run `python3 infra/observability/verify.py` against a running stack to check ingest
parsing. Add `--trace-id <32-character-id>` from a recent application log to verify
that the indexed event resolves to indexed trace documents. The script accepts an
`--elasticsearch` URL for an isolated test stack.

Existing Elasticsearch data from 8.17 cannot be mounted directly into 9.5. Follow
the supported upgrade path through 8.19 with a snapshot first. Do not delete the
existing `elasticsearch-data` volume to work around this error. An isolated test
project with a fresh volume can verify this configuration without touching data.
