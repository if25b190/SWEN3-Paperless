# Controllers

## HTTP Edge Responsibility

Think of `<Feature>Controller` as the HTTP edge and nothing more:
- Maps request DTOs to domain models.
- Calls the service interface.
- Maps domain model results to response DTOs.
- **No business logic:** Keep controllers completely free of domain logic.
- **No existence checks:** Never write `findById(...).isPresent()` in controllers — missing resources must throw exceptions from the service layer.
- **No local `@ExceptionHandler`:** Exception handling belongs exclusively in `GlobalExceptionHandler` (`@ControllerAdvice`).

## Separate Mapping Statements

Every mapping is its own statement assigned to a `var` local variable, and the service invocation is its own statement too:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public TaskDTO createTask(@RequestBody @Valid CreateTaskDTO dto) {
    var task = TaskMapper.toModel(dto);
    var created = taskService.create(task);
    return TaskMapper.toDto(created);
}
```

Never nest mappings inside service calls or vice-versa — nesting saves a couple lines but destroys debugger step-through:

```java
// ✗ nested mapping — request-map buried in argument list, response-map wrapping the call
return TaskMapper.toDto(taskService.create(TaskMapper.toModel(dto)));
```

## Request Parameters & Search Endpoints

- **Filtering:** 1 or 2 filter parameters are fine as individual `@RequestParam` arguments. 3 or more parameters (e.g., dates, status enums, keyword search) deserve a dedicated filter object bound via `@ModelAttribute`.
- **Paged searches:** Accept a `Pageable` and return a standard list envelope (e.g. `{ metaData, data }`).
- **Endpoint naming:** Name search handler methods and their service methods `search...` (e.g. `searchTasks`), never `list...`, even when no filter is provided.

## HTTP Status Codes

- `HttpStatus.CREATED` (201): On resource creation (`@ResponseStatus(HttpStatus.CREATED)`).
- `HttpStatus.NO_CONTENT` (204): On resource deletion (`@ResponseStatus(HttpStatus.NO_CONTENT)`).
- Defaults to 200 OK for standard queries and updates.

## Controller Checklist

Before committing any controller method, verify:
- [ ] `var` for local variables only — no `final`, no explicit types.
- [ ] Request mapping → service call → response mapping are separate statements.
- [ ] No mapper call nested inside a service call or another mapper call.
- [ ] No repository interactions or existence checks (`findById().isPresent()`).
