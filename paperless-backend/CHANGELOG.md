# Changelog

All notable changes to the Paperless backend are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.0.2-SNAPSHOT]

### Added
- MapStruct 1.6.3 (`mapstruct` + `kapt("org.mapstruct:mapstruct-processor")` in `build.gradle.kts`):
  all 12 mappers are now `@Mapper(componentModel = "spring")` interfaces and their
  implementations are generated at build time.
- `common/mapper/MappingConverters` — shared `@Named` converters (`toOffset`, `requireNotNull`)
  used by all mappers.

### Changed
- Mappers are now Spring beans obtained via constructor injection: controllers and JPA
  persistence adapters inject the mapper interfaces instead of calling static mapper objects.
- Generated mapper implementations use constructor injection
  (`injectionStrategy = CONSTRUCTOR`), so unit tests can construct them without a Spring context.
- Pure field mappings (`toDto`, `toModel`, `toCredentials`, `toUpdateModel`) are generated with
  `unmappedTargetPolicy = ERROR`, so unmapped target properties fail the build.

### Removed
- Hand-written field-copying mapper bodies, replaced by the generated implementations.
- Dead code with no call sites: `TeamMapper.applyUpdate`, `DocumentMapper.applyUpdate`.
