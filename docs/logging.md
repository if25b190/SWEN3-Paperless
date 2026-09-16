# Logging

## Logger Setup

- Use Lombok's `@Slf4j` annotation at the class level.
- Never instantiate `Logger` or `LoggerFactory` by hand.

## Log Levels

- `ERROR`: Unrecoverable errors and unexpected exceptions.
- `WARN`: Recoverable unexpected states, client-side anomalies, or transient errors.
- `INFO`: Business-relevant milestones and auditable events (e.g., entity created, batch job finished).
- `DEBUG` / `TRACE`: Diagnostic messages for local testing and debugging.

## Message Format & Placeholders

Keep log formats uniform across the entire codebase:

```java
log.info("[<FEATURE>] - ACTION: <method>: <field>: {}", value);
```

- **Use `{}` placeholders:** Always use parameter placeholders. Never use string concatenation, which incurs performance overhead and risks leaking sensitive data.
- **Traceability:** Include identifiers that facilitate end-to-end tracing across requests (e.g., request ID, user ID).

## Hygiene & Error Boundary

- **No sensitive data:** Never log credentials, tokens, passwords, or personal identifiable information (PII).
- **Service layer logging:** Keep logging primarily within the service and application layer.
- **No double logging on exceptions:** Code throwing a domain exception must not log it before throwing. The `GlobalExceptionHandler` logs the exception once at the boundary.
