# Layered Architecture

Each feature is organized in its own package, split into distinct layer subpackages:

```
at/fhtw/swen3/paperless/<feature>/
  controller/   <Feature>Controller     @RestController @RequestMapping("/<feature>")
  service/      <Feature>Service        interface — the feature's public surface
                <Feature>ServiceImpl    @Service, class-level @Transactional — the one impl
  repository/   <Feature>Repository     interface extends JpaRepository<<Feature>Entity, Id>
  model/        <Feature>, enums        plain POJO (Lombok), no jakarta.persistence imports
  entity/       <Feature>Entity         @Entity only, no logic
  dto/          Create/Update/Response records
  mapper/       <Feature>Mapper         static — DTO ↔ model
                <Feature>EntityMapper   static — model ↔ entity
```

## Layer Contract

The contract defines what each layer is permitted to touch — and what it must not:

| Layer | May Access / Depend On | Must Not Access |
| :--- | :--- | :--- |
| `controller` | `service/` (interface), `dto/`, `mapper/<Feature>Mapper`, `model/` | `repository/`, `entity/`, `<Feature>EntityMapper`, local `@ExceptionHandler` |
| `service` | `repository/`, `model/`, `mapper/<Feature>EntityMapper`, other feature `service/` interfaces, `Pageable`, `Sort` | `dto/`, `<Feature>Mapper`, other feature `repository/` or `entity/` |
| `repository` | `entity/`, Spring Data (`@Query`, `Specification`) | `model/`, `dto/`, `service/`, `controller/` |
| `model` | Plain Java, Lombok annotations | `jakarta.persistence.*`, Spring framework classes |
| `entity` | JPA annotations (`jakarta.persistence.*`), Lombok | Business logic, service references, helper methods |
| `dto` | Java records, Bean Validation (`jakarta.validation.*`) | Entities, repositories, database concerns |
| `mapper` | Target & source models/DTOs/entities (pure field mapping) | Services, repositories, I/O operations, business logic |

## Request Flow

### Write Request Flow
A DTO becomes a model, a model becomes an entity, and the response walks the same path back:

```
POST /<feature>
Controller(Create<Feature>DTO)
  → <Feature>Mapper.toModel(dto)            [dto → model]
  → <Feature>Service.create(model)          [@Transactional]
    → <Feature>EntityMapper.toEntity(model) [model → entity]
    → repository.save(entity)
    → <Feature>EntityMapper.toModel(saved)  [entity → model]
  → <Feature>Mapper.toDto(model)            [model → response dto]
```

### Paged Read Flow
1. The controller receives a `Pageable` directly from request query parameters.
2. The controller passes `Pageable` straight to the service (`search...`).
3. The service calls the repository, maps `Page<Entity>` to `Page<Model>` via `<Feature>EntityMapper`, and returns `Page<Model>`.
4. The controller maps items to DTOs and wraps them into a standard list envelope (e.g. `{ metaData, data }`).

## Architectural Boundaries

- **Interface-first services:** The service consists of an interface (`<Feature>Service`) and a single implementation (`<Feature>ServiceImpl`). Controllers and other features depend exclusively on the interface.
- **Repositories are plain interfaces:** Spring Data manages repository implementations. "Find or 404" is a service concern, not a repository concern.
- **POJOs vs. Entities:** `model/` holds plain POJOs without JPA imports; `entity/` holds `@Entity` classes only, without business logic.
- **Feature-to-feature boundaries:** A feature may call another feature's `service/` interface and use its `dto/` or `model/` types, but **never** access another feature's `repository/` or `entity/` directly.
