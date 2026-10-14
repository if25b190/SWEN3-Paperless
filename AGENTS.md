# Backend — Spring Boot

Standards: [`docs/kotlin-style.md`](docs/kotlin-style.md) · [`docs/annotations.md`](docs/annotations.md) ·
[`docs/layered-architecture.md`](docs/layered-architecture.md) · [`docs/controllers.md`](docs/controllers.md) ·
[`docs/mappers.md`](docs/mappers.md) · [`docs/exceptions.md`](docs/exceptions.md) ·
[`docs/testing.md`](docs/testing.md) · [`docs/logging.md`](docs/logging.md)

## Stack

Kotlin (JVM 25) · Spring Boot 4.x · Gradle · Spring Data JPA · JUnit 5 + Mockito + AssertJ.

## Feature package layout

One package per feature, split into layer subpackages:

```
at/fhtw/swen3/paperless/<feature>/
  controller/   <Feature>Controller     @RestController @RequestMapping("/<feature>")
  service/      <Feature>Service        interface — the feature's public surface
                <Feature>ServiceImpl    @Service, class-level @Transactional — the one impl
  entity/       <Feature>Entity         JPA annotations only, no business logic
  persistence/  <Feature>Persistence    domain-facing interface
                Jpa<Feature>Persistence @Repository adapter — maps models ↔ entities
    repository/ <Feature>Repository     plain Spring Data interface
    mapper/     <Feature>EntityMapper   pure model ↔ entity mapping
  model/        <Feature>, enums        domain models (data class), no jakarta.persistence imports
  dto/          Create/Update/Response records
  mapper/       <Feature>Mapper         static — DTO ↔ model
```

`model/`, `entity/`, `service/`, and `persistence/` are sibling folders within each
feature. Entities stay separate from the persistence adapters and repositories.

Not every feature needs every file — a read-only feature has no `Create<Feature>DTO`. Add a
layer only when it actually carries weight.

## Gotchas

- The service is an interface `<Feature>Service` plus one `@Service` implementation
  `<Feature>ServiceImpl` (both in `service/`). Inject and mock the interface; `@InjectMocks`
  in the service's own unit test targets `<Feature>ServiceImpl` — Mockito can't instantiate
  an interface.
- Services must never inject or call another service. Use domain-facing persistence
  interfaces for cross-feature data access and keep validation and authorization in the calling service.
- JPA repositories remain plain Spring Data interfaces inside `persistence/repository/`.
  Services inject `<Feature>Persistence` domain interfaces; adapters handle JPA and mapping.
  "Find or 404" remains a service concern:
  `repository.findById(id).orElseThrow(() -> new AppException(AppErrorMessage.<X>_NOT_FOUND))`.
- `model/` holds Kotlin domain models / data classes (no JPA imports); `entity/` holds `@Entity` classes only, no logic.
- Primary constructor injection is used in services (`class <Feature>ServiceImpl(private val persistence: <Feature>Persistence) : <Feature>Service`);
  Lombok is not used.
- Prefer `val` for immutability; use `var` only when mutable state is strictly necessary (see [`docs/kotlin-style.md`](docs/kotlin-style.md)).
- No existence checks or business logic in controllers — that belongs in the service, which
  should throw when something isn't there.
- `@Transactional` at class level, service classes only (`readOnly = true` for read paths).

- Controllers, services, and domain models must not import JPA, persistence entities, Spring Data
  repositories, or entity mappers. Only persistence adapters may access these implementation details.
