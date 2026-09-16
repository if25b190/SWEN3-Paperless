# Annotations

## Lombok

Use Lombok to reduce repetitive boilerplate, keeping usage disciplined and consistent:

- `@RequiredArgsConstructor`: Used for constructor-based dependency injection on `private final` fields.
- `@Slf4j`: Used for logging across components (never instantiate `Logger` or `LoggerFactory` manually).
- `@Builder(setterPrefix = "with")`: Used when an object is complex enough to benefit from a builder pattern.
- **`@Data` is forbidden:** It bundles too many behaviors (`@ToString`, `@EqualsAndHashCode`, `@Getter`, `@Setter`, `@RequiredArgsConstructor`) at once. Use `@Getter` and `@Setter` individually instead.
- **`model/` POJOs:** Carry the full Lombok set:
  ```java
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder(setterPrefix = "with")
  ```
  and **never** import `jakarta.persistence.*`.
- **`entity/` classes:** Carry the same Lombok set plus JPA annotations, and nothing else — no business logic.

## Spring Annotations

- `@RestController`: On web controllers, paired with HTTP method annotations at method level (`@GetMapping`, `@PostMapping`, `@PatchMapping`, `@DeleteMapping`).
- `@Service`: Placed on the feature implementation `<Feature>ServiceImpl`, which implements the plain `<Feature>Service` interface (the interface itself carries no annotations).
- `@Repository`: **Never written by hand.** Repositories are plain Spring Data interfaces (`extends JpaRepository<<Feature>Entity, Id>`) automatically registered by Spring Data JPA. Do not create manual `*RepositoryImpl` or adapter classes.
- `@Component`: For generic Spring-managed beans; `@Configuration` for configuration classes.
- `@Autowired`: Means constructor injection in production code (facilitated by Lombok's `@RequiredArgsConstructor`). Field injection is reserved strictly for test classes.
- `@ConfigurationProperties`: Used when binding 3 or more related configuration properties. For fewer than 3 properties, individual `@Value` annotations are acceptable.
- `@Transactional`: Lives at the **class level on `@Service` implementations only**, so transaction management never leaks into controllers or repository interfaces. Use `@Transactional(readOnly = true)` for read paths and `@Transactional` for write paths.
- `@Validated`: Placed at class or method parameter level to trigger Bean Validation. Use `@RequestBody @Valid` on incoming write DTOs.
- `@PreAuthorize`: Placed at the controller layer when Spring Security enforces method-level authorization.

## Dependency Hygiene

- Keep dependencies strictly acyclic.
- Do not use `@Order` to paper over a dependency resolution problem — fix the underlying dependency design instead.
