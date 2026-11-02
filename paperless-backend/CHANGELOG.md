# Changelog

All notable changes to the Paperless backend are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.0.3-SNAPSHOT]

### Added
- Project-local override of the openapi-generator `kotlin-spring` template
  (`openapi-templates/apiInterface.mustache`, wired via `templateDir` in `build.gradle.kts`) that
  appends a nullable `Authentication` parameter to every generated API method.
- `AuthenticatedPrincipalTest` covering the principal name parsing rules (happy path, uppercase
  canonical UUID, malformed, non-canonical, missing).

### Changed
- Principal resolution moved to the controller boundary: controllers extract the actor with
  `AuthenticatedPrincipal.userId(authentication)` from Spring MVC's injected `Authentication` and
  pass `actorId` explicitly into the service layer. `DocumentService`, `SearchService`, `TeamService`,
  and `UserService` signatures now declare `actorId` as their first parameter where authorization
  needs it, and no main-code class touches `SecurityContextHolder` anymore (a missing or
  unauthenticated `Authentication` yields 401 instead of 403). Unit tests pass the actor directly
  instead of stubbing the security context. The template is pinned to openapi-generator 7.11.0 and
  must be re-diffed against the upstream template on generator upgrades.
- Redundant service-level format validation removed (`UserServiceImpl.validateUsername`/`validatePassword`,
  `SearchServiceImpl` page/size checks, `DocumentServiceImpl` update-title check): the constraints
  live in `openapi.yaml` and are enforced by the generated DTOs/interfaces. New
  `pattern: '^\s*\S[\s\S]*$'` constraints on `CreateUserRequest.username`,
  `UpdateUserRequest.username`, and `UpdateDocumentRequest.title` close the whitespace-only gap the
  service checks used to cover.
- `GET /api/v1/documents/{id}` now returns `DocumentDetailResponse` (slim `DocumentResponse` fields
  plus `ocr_content` and `summary`). List, search, upload, and update responses return the slim
  `DocumentResponse`, so OCR text and summaries are no longer included in collection payloads.

### Removed
- `storage_key` from `DocumentResponse`: the MinIO object key is an internal implementation detail
  and was never consumed by the frontend.

## [0.0.2-SNAPSHOT]

### Added
- MapStruct 1.6.3 (`mapstruct` + `kapt("org.mapstruct:mapstruct-processor")` in `build.gradle.kts`):
  all 12 mappers are now `@Mapper(componentModel = "spring")` interfaces and their
  implementations are generated at build time.
- `common/mapper/MappingConverters` — shared `@Named` converters (`toOffset`, `requireNotNull`)
  used by all mappers.
- 13 dedicated exception classes in `exception/` (one per `AppErrorMessage`, e.g. `UserNotFoundException`).
- Flyway V3 migration recreating `fk_team_members_team` with `ON DELETE CASCADE` so deleting a
  team removes its memberships at the database level.
- Layer-specific exceptions for the OCR queue pipeline: `OcrPublishException` (messaging layer,
  broker publish/confirm failure) and `OcrJobInvalidException` (service layer, outbox job without an
  id). `AppException` now accepts an optional cause.
- Boundary logging for the OCR dispatch loop: `OcrDispatchScheduler` logs transient publish failures
  at `WARN` and unexpected failures at `ERROR` with stack frames; `OcrDispatchServiceImpl` logs
  successful dispatches and `OcrWorker` logs message receipts at `INFO`.

### Changed
- Mappers are now Spring beans obtained via constructor injection: controllers and JPA
  persistence adapters inject the mapper interfaces instead of calling static mapper objects.
- Generated mapper implementations use constructor injection
  (`injectionStrategy = CONSTRUCTOR`), so unit tests can construct them without a Spring context.
- Pure field mappings (`toDto`, `toModel`, `toCredentials`, `toUpdateModel`) are generated with
  `unmappedTargetPolicy = ERROR`, so unmapped target properties fail the build.
- Throw sites and tests reference failure modes by exception type (`UserNotFoundException()`,
  `assertAppError(UserNotFoundException::class)`) instead of `AppErrorMessage` values.
- `TeamServiceImpl.deleteTeam` relies on the database cascade for membership removal instead of
  an explicit `deleteByTeamId` call.
- `RabbitOcrMessagePublisher` throws `OcrPublishException` instead of `IllegalStateException`, and
  `OcrDispatchServiceImpl` throws `OcrJobInvalidException` instead of relying on `requireNotNull`.

### Removed
- Hand-written field-copying mapper bodies, replaced by the generated implementations.
- Dead code with no call sites: `TeamMapper.applyUpdate`, `DocumentMapper.applyUpdate`.
- `deleteByTeamId` from `TeamMemberPersistence`, `JpaTeamMemberPersistence`, and
  `TeamMemberRepository` (no remaining call sites).
