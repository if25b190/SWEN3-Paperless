# Exception Handling

## Domain Exception Model

Avoid creating a new exception class for every individual failure mode. Instead, maintain a single domain exception class carrying an error-code enum:

```java
throw new AppException(AppErrorMessage.COMMUNITY_NOT_FOUND);
```

New failure modes simply require adding a new enum value to `AppErrorMessage`, rather than introducing a new exception class to wire up and handle.

## Global Exception Handling

Centralize all error handling through a single `GlobalExceptionHandler` annotated with `@ControllerAdvice` and `@ExceptionHandler`:

- Single point of truth for mapping domain exceptions and validation failures to HTTP status codes.
- Produces a consistent error response structure across the entire API.
- **No local `@ExceptionHandler` methods in controllers:** Local handlers fragment status mapping and create multiple places to check when responses look unexpected.

## Logging at the Throw Site

**Do not log an exception when throwing it.**

```java
// ✗ Redundant — logs the exception twice
log.error("Task not found with id: {}", id);
throw new AppException(AppErrorMessage.TASK_NOT_FOUND);

// ✓ Let the handler log it
throw new AppException(AppErrorMessage.TASK_NOT_FOUND);
```

The `GlobalExceptionHandler` logs the exception in one place. Logging at the throw site writes the exact same failure to the logs twice.
