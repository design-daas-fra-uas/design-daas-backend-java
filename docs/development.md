[README](../README.md) · [User manual](user-manual.md) · [API](api.md) · [Database](database.md) · [Architecture (arc42)](arc42.md) · [Development](development.md) · [Operations](operations.md)

# Developer guide

## Project layout

```
src/main/java/de/frauas/design/backend/
├── DesignBackendApplication.java   # @EnableScheduling, @EnableAsync
├── auth/          controller · dto · exception · model · repository · service
├── user/          controller · dto · exception · model · repository · service
├── desktop/       controller · dto · exception · model · repository · service
├── permission/    controller · dto · model · repository · service
├── shared/        GlobalExceptionHandler · dto · event · exception · security · util
├── config/        Security, JWT keys, CORS, OpenAPI, async, clock, password + properties/
└── bootstrap/     DataInitializer
src/main/resources/
├── application*.yml
├── db/migration/  V1..V4 Flyway scripts
└── templates/mail/ Thymeleaf HTML e-mails
src/test/java/...  unit tests (Mockito), integration tests (MockMvc + H2), ArchitectureTest
```

## Common commands

```bash
mvn clean verify                       # compile + all tests
mvn test -Dtest=TokenServiceTest       # one test class
mvn test -Dtest='*IntegrationTest'     # integration tests only
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn package -DskipTests                # build the jar only
```

## Conventions (some enforced by `ArchitectureTest`)

- Class names end in `*Controller`, `*Service` and `*Repository`, and live in the matching package.
- Controllers stay thin: parse the request, call **one** service, shape the response.
- Throw a **typed exception**, never build error responses inside services:
  - not found → extend `NoSuchElementException` (404)
  - conflict → extend `ConflictException` (409)
  - bad input → extend `IllegalArgumentException` (400)
  - token/session → extend `TokenGrantException` / `SessionException`
- Never log raw e-mails, passwords or tokens. Use `LogMasking.maskEmail`.
- Use the injected `Clock`, never a bare `Instant.now()`, in business logic.
- Schema change → new Flyway file `V<next>__short_description.sql`. **Never edit a migration that
  has already been applied.**
- Put authorization on the controller: `@PreAuthorize(Authorities.IS_ADMIN)`.

## How to add an endpoint

1. Add request and response DTOs in `<module>/dto` with Bean Validation annotations.
2. Add the business logic in `<module>/service` with `@Transactional`.
3. Add the controller method with `@PreAuthorize`. If it must be public, add it to `SecurityConfig`.
4. Add a unit test for the service and an integration test (extend `BaseIntegrationTest`; see `MockJwt` for tokens).
5. Check that it appears correctly in Swagger.

## Testing notes

- `BaseIntegrationTest` starts the full context with the `test` profile (H2, rate limiting off).
- `MockJwt` helps create authenticated requests.
- `junit-platform.properties` disables JUnit interceptors so ArchUnit `@ArchTest` fields are found
  with Surefire 3.5.

## Code documentation

### Conventions

- **Every type and every public method has Javadoc.** Getters, setters and other
  Lombok-generated members are the exception.
- **Each module has a `package-info.java`** that explains what the module does and how it
  connects to the others. Read these first when you start in a new area.
- **Explain why, not what.** For example, `TokenService` explains why a dummy hash is checked
  for unknown users, and `RefreshTokenRepository` explains why the lookup takes a row lock.
- **Write down known limitations next to the code**, for example the refill issue in
  `TokenBucket`, so the next person doesn't have to discover it.
- **Update the docs in the same change** whenever you change behaviour:
  - Javadoc and the matching file in `docs/`
  - `docs/api.md` for endpoints
  - `docs/database.md` for migrations
  - `docs/arc42.md` for architecture decisions and risks

### Current state (reviewed 2026-11-07)

| Check | Result |
|---|---|
| Types with Javadoc | 122 / 122 |
| Public methods with Javadoc | 96 / 96 (excluding `@Override` and Lombok) |
| Module overviews (`package-info.java`) | 7 / 7 (`auth`, `user`, `desktop`, `permission`, `shared`, `config`, `bootstrap`) |
| Outdated comments | Fixed: desktop subgroup "names", `TokenBucket` state layout, "sliding" rate limit, cleanup time zone, `TokenService` scope, `requestApplication` return value |

Most Javadoc was already detailed and accurate. The review mainly found comments that had not
kept up with later changes, for example desktop groups losing their `name` column in V4.

### Generating the HTML Javadoc

```bash
mvn javadoc:javadoc
# open target/reports/apidocs/index.html
```
