# Layered Architecture

Each feature exposes a service interface and isolates JPA behind an Anti-Corruption Layer:

```
at/fhtw/swen3/paperless/<feature>/
  controller/   <Feature>Controller
  service/      <Feature>Service, <Feature>ServiceImpl
  model/        immutable domain models and enums
  dto/          request and response types
  mapper/       <Feature>Mapper — DTO ↔ model
  persistence/  <Feature>Persistence — domain-facing interface
                Jpa<Feature>Persistence — Spring @Repository adapter
    entity/     JPA entities
    repository/ plain Spring Data interfaces
    mapper/     <Feature>EntityMapper — pure model ↔ entity mapping
```

## Layer contract

| Layer | May depend on | Must not depend on |
| --- | --- | --- |
| Controller / DTO mapper | Service interfaces, models, DTOs | Persistence interfaces or implementation details |
| Service | Domain persistence interfaces, models, other service interfaces, paging types | JPA, entities, Spring Data repositories, entity mappers, DTOs |
| Persistence interface | Domain models, UUIDs, Optional, Page / Pageable | JPA, entities, Spring Data repositories, services, DTOs |
| JPA adapter | Entities, repositories, entity mappers, EntityManager | Controllers, DTOs, business services |
| Entity mapper | Models and entities | I/O, repositories, services, business rules |
| Domain model | Kotlin / Java value types | JPA and Spring framework classes |

## Request flow

```
Controller → DTO mapper → Service → Persistence interface
                                      ↓
                              JPA adapter → entity mapper → Spring Data repository
```

Adapters translate all persistence results to domain models before returning, including
optional values, lists, pages, and lazy relationships. Services never receive managed
entities and update immutable domain values with `copy`.

Business validation, authorization, and domain not-found errors remain in services.
Adapters resolve entity references and handle database operations, mapping, flushes,
and concurrent-deletion / stale-version detection. They do not decide who may access data.
Spring Data repositories stay plain interfaces; no handwritten repository implementations.

Services own transactions. Adapter calls participate in those transactions so lazy
relationships are materialized while the persistence context is open. Existing
pessimistic locks for user authentication and team membership changes are preserved.
The document adapter carries the domain version through mapping and compares it,
owner ID, and team ID after refresh before update or deletion. Service file cleanup
continues to depend on transaction completion and explicit flush failures.

Cross-feature orchestration uses service interfaces and domain-facing persistence
interfaces where the operation must stay in one transaction (for example account
deletion checking team memberships and revoking tokens). JPA types are confined to
persistence packages, even when associations refer to another feature's entities.

`DocumentReferencesPersistence` provides domain-valued reference lookups to document
services. `AccessToken` carries a digest, user ID, and expiry, without leaking a user
entity. New documents have a nullable ID until persisted; response mapping requires an ID.

See [Annotations](annotations.md), [Mappers](mappers.md), and [Testing](testing.md).
