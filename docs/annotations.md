# Annotations

## Kotlin Idioms (No Lombok)

Lombok is not used in this project because Kotlin natively provides these capabilities:

- **Primary constructors:** Used for dependency injection in classes (replacing `@RequiredArgsConstructor`).
- **Data classes (`data class`):** Provide getters, setters (for `var`), `copy()`, `equals()`, `hashCode()`, and `toString()` natively without boilerplate.
- **Default and named arguments:** Replace builder patterns and `@Builder`. Construct objects directly using named parameters.
- **`model/` domain models:** Declared as Kotlin `data class` with `val` properties, and **never** import `jakarta.persistence.*`.
- **`entity/` classes:** Plain Kotlin classes carrying JPA annotations and nothing else — no business logic. The `kotlin-jpa` plugin generates the zero-argument constructor required by JPA.
- **Logging:** Use SLF4J `LoggerFactory.getLogger(...)` directly (replacing `@Slf4j`). See [`docs/logging.md`](logging.md).

## Spring Annotations

- `@RestController`: On web controllers, paired with HTTP method annotations at method level (`@GetMapping`, `@PostMapping`, `@PatchMapping`, `@DeleteMapping`).
- `@Service`: Placed on the feature implementation `<Feature>ServiceImpl`, which implements the plain `<Feature>Service` interface (the interface itself carries no annotations).
- `@Repository`: **Never written by hand.** Repositories are plain Spring Data interfaces (`extends JpaRepository<<Feature>Entity, Id>`) automatically registered by Spring Data JPA. Do not create manual `*RepositoryImpl` or adapter classes.
- `@Component`: For generic Spring-managed beans; `@Configuration` for configuration classes.
- `@Autowired`: Constructor injection is used in production code via Kotlin primary constructors. Spring automatically resolves single-constructor beans without `@Autowired`. Field injection (`@Autowired lateinit var`) is reserved strictly for test classes.
- `@ConfigurationProperties`: Used when binding 3 or more related configuration properties. For fewer than 3 properties, individual `@Value` annotations are acceptable.
- `@Transactional`: Lives at the **class level on `@Service` implementations only**, so transaction management never leaks into controllers or repository interfaces. Use `@Transactional(readOnly = true)` for read paths and `@Transactional` for write paths.
- `@Validated`: Placed at class or method parameter level to trigger Bean Validation. Use `@RequestBody @Valid` on incoming write DTOs.
- `@PreAuthorize`: Placed at the controller layer when Spring Security enforces method-level authorization.

## Dependency Hygiene

- Keep dependencies strictly acyclic.
- Do not use `@Order` to paper over a dependency resolution problem — fix the underlying dependency design instead.
