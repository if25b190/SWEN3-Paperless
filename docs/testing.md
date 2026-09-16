# Testing

## Testing Stack

- **Frameworks:** JUnit 6, Mockito, AssertJ.

## Layer-Specific Testing

Tests mirror the feature's layer subpackages:

- **Service tests:** Always required. Core business logic, validation rules, and error conditions live in the service layer.
- **Controller tests (`@WebMvcTest`):** Needed once an endpoint introduces request validation, HTTP status code mappings, security constraints, or a response shape worth asserting.
- **Repository tests (`@DataJpaTest`):** Reserved for custom `@Query` methods or `Specification` logic — never write tests for plain CRUD operations already provided and tested by Spring Data JPA.

## Mocking Rules

- In the service's own unit test, `@InjectMocks` must target the implementation class `<Feature>ServiceImpl` (Mockito cannot instantiate an interface).
- Everywhere else (controllers, other services), mock the interface (`@Mock` / `@MockitoBean <Feature>Service`).
- Field injection (`@Mock`, `@InjectMocks`, `@Autowired`) is allowed in test classes (forbidden in production code).

## Test Conventions

- **Naming:** Use snake_case ending with `_ok` or `_ko` (e.g., `create_task_ok()`, `get_task_not_found_ko()`). Longer descriptive names are acceptable when they clearly convey intent.
- **Structure:** Follow `// given`, `// when`, `// then` block separators.
- **Assertions:** Use AssertJ (`assertThat`, `assertThatThrownBy`). Group related checks into `assertAll(...)` so failures report all mismatches simultaneously rather than stopping on the first one.
- **Variable style:** Use `var` for local variables and omit `final` (same style as controllers).
- **No business logic in tests:** Do not write loops, conditionals (`if`), or reflection inside test methods. If complex test data is needed, create private factory helper methods.

## Required Service Test Coverage

For each service, ensure tests cover:
1. **Happy path:** Normal successful execution of each operation.
2. **Find or 404:** Throws `AppException` when an entity is not found.
3. **Guard clauses:** Preconditions that fail before calling repository save:
   ```java
   verify(repository, never()).save(any());
   ```
4. **PATCH semantics:** Partial updates modify only provided fields and preserve unmodified existing values.

## Quality Gate

Nothing is merged without passing formatting checks and test suites:

```bash
./mvnw spotless:check && ./mvnw test
```
