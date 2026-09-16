# Kotlin Style & Formatting

> [!NOTE]
> The project backend uses Kotlin. This guide defines the Kotlin coding and formatting standards. See also [`docs/kotlin-style.md`](kotlin-style.md).

## Formatting Rules

- **Indentation:** 4 spaces (no tabs).
- **Line length:** 120 characters maximum.
- **Code style:** Official Kotlin Coding Conventions / IntelliJ IDEA default Kotlin style.
- **Encoding:** UTF-8.
- **Blank lines:** Blank lines separate logical blocks. Always put a blank line before and after a `return` statement, a `when` expression, a loop (`for`, `while`), or a multiline chained call sequence — unless it is the first or last line in its enclosing block.

## Automated Enforcement

Do not rely on manual formatting review:
- Enforce formatting using **Spotless** (with ktlint) or **ktlint**:
  - Fix formatting:
    ```bash
    ./mvnw spotless:apply
    ```
  - Verify formatting in CI and pre-commit/pre-push quality gates:
    ```bash
    ./mvnw spotless:check
    ```

## Variables & Immutability (`val` vs. `var`)

- **Prefer `val` over `var` everywhere:**
  - Function parameters in Kotlin are implicitly `val` (immutable by definition).
  - Use `val` for all local variables, properties, and references by default.
  - Use `var` only when local mutable state is strictly necessary and cannot be cleanly expressed with immutability.
- **Type inference vs. explicit types:**
  - Use type inference (`val task = ...`) for local variables in **controllers** and **tests**, and where the type is obvious from the right-hand side expression.
  - Declare **explicit return types** on all `public` and `protected` functions in **services**, **repositories**, and **mappers** to ensure clear API contracts:
    ```kotlin
    fun searchTasks(pageable: Pageable): Page<Task>
    ```

## Null Safety & Idiomatic Checks

- Leverage Kotlin's type system (`String` vs. `String?`) — avoid nullable types unless nullability is a legitimate domain state.
- **Never use `!!` (not-null assertion):** Handle missing values gracefully or fail with a domain exception:
  ```kotlin
  val task = taskRepository.findByIdOrNull(id)
      ?: throw AppException(AppErrorMessage.TASK_NOT_FOUND)
  ```
- Use standard library null/blank checks:
  - `isNullOrEmpty()` and `isNullOrBlank()` on strings and collections.
  - `checkNotNull(...)` or `requireNotNull(...)` when enforcing internal invariants.

## Data Classes & Constructors

- **Parameter count:** Maximum 3 parameters on functions or constructors. If more related parameters are needed, group them into a Kotlin `data class`.
- **Data classes for models and DTOs:** Use `data class` with `val` properties for immutable models, DTOs, and value objects.
- **No builders needed:** Kotlin supports default argument values and named arguments. Do not use builder patterns or `@Builder`:
  ```kotlin
  val task = Task(
      id = dto.id,
      title = dto.title,
      status = TaskStatus.OPEN
  )
  ```
- Declare dependencies in the primary constructor in the class header:
  ```kotlin
  @Service
  @Transactional
  class TaskServiceImpl(
      private val taskRepository: TaskRepository,
      private val userClient: UserClient
  ) : TaskService { ... }
  ```

## Language Idioms & Control Flow

- **Expression bodies:** Use single-expression syntax (`fun calculate() = ...`) when a function consists of a single concise expression.
- **`when` expressions:** Prefer `when` expressions over chained `if` / `else if` / `else` or Java-style switches:
  ```kotlin
  val status = when (input) {
      "ACTIVE" -> Status.ACTIVE
      "INACTIVE" -> Status.INACTIVE
      else -> throw AppException(AppErrorMessage.INVALID_STATUS)
  }
  ```
- **Control flow:** Prefer early returns with guard clauses (`?: return ...`, `?: throw ...`) over deeply nested branches.
- **Collections:**
  - Prefer read-only collection interfaces (`List`, `Set`, `Map`) over mutable collections (`MutableList`).
  - Use idiomatic functional operations (`map`, `filter`, `associate`, `flatMap`, `firstOrNull`) rather than imperative loops.
- **Named conditions:** Wrap compound boolean conditions in a descriptive named `val` rather than inlining complex expressions.

## Constants & No Magic Values

- Replace magic numbers and strings with named constants.
- Define constants as `const val` within a `companion object` or as top-level private `const val` in the relevant file:
  ```kotlin
  companion object {
      private const val MAX_RETRY_ATTEMPTS = 3
  }
  ```

## Exceptions

- Kotlin does not have checked exceptions. Do not use `@Throws` except for specific Java interop requirements.
- Throw unchecked domain exceptions (`throw AppException(AppErrorMessage.<X>)`).

## Comments & Documentation

- Omit comments on obvious or self-explanatory code.
- Reserve comments for cron expressions, non-trivial regex patterns, `TODO` markers, or `// given` / `// when` / `// then` test structure separators.
- Skip KDoc on obvious classes and methods.

## Annotations & Overrides

- The `override` keyword is compiler-enforced; always mark overridden interface or superclass methods with `override`.
- Ensure annotation targets are explicit when needed (e.g. `@field:Valid` on data class properties when Bean Validation requires field targeting).

## Imports

- No wildcard imports (`import foo.bar.*` is forbidden).
