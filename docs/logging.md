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
processing joins the trace of the request that dispatched it.

The current W3C trace ID is available as `trace_id` in SLF4J MDC (injected by
the agent's Logback MDC instrumentation) and appears in the console pattern
next to `requestId`; worker events written to `WORKER_LOG_PATH` carry the same
value in the `trace_id` field. `trace_id=none` marks log lines written outside
of an active trace (e.g., startup).

Services record login, resource creation/update/deletion, and membership changes
with resource IDs. These are operational milestones within a transaction, not a
durable audit trail or proof of transaction commit. Client errors are logged once
at the exception handler or security boundary. Unexpected exceptions are logged
at `ERROR` with their type and stack frames; exception messages are omitted
because they may contain request data.

Request URLs, query text, request/response bodies, usernames, credentials, bearer
tokens, document titles, filenames, and content are not logged by the application.
