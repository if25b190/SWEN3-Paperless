# Mappers

Pick one approach as a team and stick with it — mixing strategies within a feature leads to unmaintainable code.

## Option A: MapStruct

MapStruct generates mapper implementations from annotated interfaces:

- Define mapper interfaces with the `@Mapper` annotation.
- Use `componentModel = "spring"` so Spring registers the generated mapper as a bean.
- Use `@Mapping(source = "...", target = "...")` for any fields that do not match names directly.
- Suffix the interface with `Mapper` (e.g., `UserMapper`).
- Keep method names direction-obvious (`toDto`, `toEntity`, `toModel`).

```java
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "email", target = "emailAddress")
    UserDTO toDto(User user);

    @Mapping(source = "emailAddress", target = "email")
    User toEntity(UserDTO userDto);
}
```

In unit tests without a Spring context, obtain the mapper instance via `Mappers.getMapper(UserMapper.class)`.

## Option B: Static Mappers

Static mappers trade code generation for plain Java that can be read top-to-bottom without IDE plugins. Each feature gets two static mappers corresponding to architectural boundaries:
- `<Feature>Mapper`: static — `dto` ↔ `model`
- `<Feature>EntityMapper`: static — `model` ↔ `entity`

### Implementation Rules
- Declare the class as `public final class`.
- Provide a `private` constructor throwing `new UnsupportedOperationException("This class should never be instantiated");`.
- Name static methods for the direction they map (`toModel`, `toEntity`, `toDto`, `fromCreateDto`, `fromUpdateDto`).
- Check for `null` at the start of each mapping method (`if (source == null) return null;`).
- Build into a local variable before returning, rather than nesting builder chains.
- Pure field mapping only: no I/O, no database calls, no service invocations inside mappers.

```java
public final class UserMapper {

    private UserMapper() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static UserDTO toDto(User user) {
        if (user == null) {
            return null;
        }

        UserDTO dto = new UserDTO(user.getId(), user.getEmail());
        return dto;
    }

    public static User toEntity(UserDTO userDto) {
        if (userDto == null) {
            return null;
        }

        User user = User.builder()
            .withId(userDto.id())
            .withEmail(userDto.email())
            .build();

        return user;
    }
}
```
