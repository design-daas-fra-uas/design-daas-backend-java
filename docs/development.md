# Developer guide

> **Audience:** developers working on the backend
> **Last reviewed:** 2026-11-13 · [Documentation index](../README.md#documentation)

## In short

- Build and test everything with `mvn clean verify`. A change is only done when this passes.
- The code is split into four feature modules, and each module has the same layers:
  controller, service, repository, model. The build checks this automatically.
- Every change comes with tests and with updated documentation.

## Contents

- [Prerequisites](#prerequisites)
- [Project layout](#project-layout)
- [Build and run](#build-and-run)
- [Testing](#testing)
- [Continuous integration](#continuous-integration)
- [Coding conventions](#coding-conventions)
- [Adding an endpoint](#adding-an-endpoint)
- [Code documentation](#code-documentation)
- [Debugging](#debugging)
- [Workflow and releases](#workflow-and-releases)
- [Definition of done](#definition-of-done)

## Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | 21 (Temurin recommended) | Build and run |
| Maven | 3.9 or later | Build; no Maven wrapper is committed |
| Docker | Any current version | Local MariaDB; the MariaDB migration test (skipped without Docker) |
| IDE | Any, with Lombok support | IntelliJ IDEA: enable annotation processing |

## Project layout

```
src/main/java/de/frauas/design/backend/
├── DesignBackendApplication.java   entry point (@EnableScheduling, @EnableAsync)
├── auth/          token grants, session, revocation, cleanup
├── user/          registration, users, admins, user groups, e-mail
├── desktop/       desktops and desktop groups
├── permission/    permission check and permission management
├── shared/        error handling, events, logging (request id), rate limiting, utilities
├── config/        security, JWT keys, CORS, OpenAPI, async, clock; properties/
└── bootstrap/     DataInitializer (default admin)

src/main/resources/
├── application.yml, application-{local,dev,prod}.yml
├── db/migration/    Flyway scripts V1–V5
└── templates/mail/  Thymeleaf e-mail templates

src/test/java/de/frauas/design/backend/
├── arch/            ArchitectureTest (19 ArchUnit rules)
├── integration/     MockMvc tests against H2, and FlywayMariaDbIntegrationTest (Testcontainers)
└── <module>/        unit tests per module (JUnit 5, Mockito, AssertJ)
```

Each feature module uses the same sub-packages: `controller`, `service`, `repository`, `model`,
`dto` and `exception`. Each module's `package-info.java` explains its purpose; read it first when
you start working in an unfamiliar area.

## Build and run

```bash
mvn clean verify                                     # compile, all tests, coverage check, package
mvn spring-boot:run                                  # run with the default profile "local"
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # run with another profile
mvn package -DskipTests                              # build the jar only
```

The application needs a MariaDB instance; see [Quick start](../README.md#quick-start).

## Testing

```bash
mvn test -Dtest=TokenServiceTest           # one class
mvn test -Dtest='*IntegrationTest'         # integration tests only
mvn verify                                 # everything, plus the JaCoCo report
```

| Test type | Scope | Database | Count (2026-11-13) |
|---|---|---|---|
| Unit tests | One class, dependencies mocked with Mockito | None | 253 in 33 classes |
| Integration tests | Full Spring context and security chain via MockMvc (`BaseIntegrationTest`) | H2 in MySQL mode, schema generated from the entities | 96 in 12 classes |
| Migration test | `FlywayMariaDbIntegrationTest` runs all Flyway migrations on MariaDB 11.4 and validates the entities | MariaDB in Testcontainers; **skipped without Docker** | 1 |
| Architecture test | `ArchitectureTest` checks layering, naming and dependency rules | None | 19 rules |

Notes:

- The `test` profile disables rate limiting and Flyway and stores keys in `./test-keys`.
- Use `MockJwt` to create authenticated requests in integration tests.
- `junit-platform.properties` disables JUnit interceptors so that Surefire 3.5 finds the
  `@ArchTest` fields.
- **Coverage gate:** `mvn verify` fails if line or branch coverage drops below 80 %. The report
  is written to `target/site/jacoco/index.html`.

## Continuous integration

`.github/workflows/ci.yml` runs `mvn -B verify` on Java 21 for every push to `master` and every
pull request. The runner has Docker, so the MariaDB migration test runs there. The JaCoCo report
is uploaded as the build artifact `jacoco`.

## Coding conventions

| Topic | Convention |
|---|---|
| Naming and placement | Classes end in `Controller`, `Service` or `Repository` and live in the package of the same name (enforced by ArchUnit) |
| Controllers | Thin: validate the request, call one service method, shape the response. No business logic |
| Authorization | `@PreAuthorize(Authorities.IS_ADMIN)` or `IS_ANY_USER` on the controller. Public paths are listed in `SecurityConfig` |
| Errors | Throw a typed exception; never build error responses in services. Not found → `NoSuchElementException` (404); invalid input → `IllegalArgumentException` (400); conflict → `ConflictException` (409); token or session → `TokenGrantException` / `SessionException` |
| Time | Use the injected `Clock`, never `Instant.now()` in business logic |
| Side effects | Send e-mails and other irreversible effects through `AfterCommit.run(…)` |
| Logging | SLF4J (`@Slf4j`). Mask e-mails with `LogMasking.maskEmail`. Never log passwords or tokens |
| Schema | New migration `V<next>__<description>.sql`. Never edit an applied migration |
| Lombok | `@RequiredArgsConstructor` for injection; `@Getter`/`@Setter` on entities; `@Data` only on DTOs |

## Adding an endpoint

1. **DTOs:** add request and response types in `<module>/dto`, with Bean Validation
   annotations on the request.
2. **Service:** implement the logic in `<module>/service` with `@Transactional`. Throw typed
   exceptions.
3. **Controller:** add the method with `@PreAuthorize`. If the endpoint must be public, add a
   matcher in `SecurityConfig` and consider rate limiting.
4. **Schema:** if needed, add a Flyway migration and update [database.md](database.md).
5. **Tests:** a unit test for the service and an integration test (extend `BaseIntegrationTest`)
   covering success, validation errors and access denial.
6. **Documentation:** Javadoc on new types and public methods, an entry in [api.md](api.md), and a
   line in `CHANGELOG.md`. Record architecture decisions or risks in [arc42.md](arc42.md).
7. **Check:** `mvn verify` passes, and the endpoint appears correctly in Swagger UI.

## Code documentation

### Rules

- **Every type and every public method has Javadoc.** Lombok-generated members and `@Override`
  methods are exempt.
- **Each module has a `package-info.java`** that describes its purpose and its relationships to
  the other modules.
- **Explain why, not what.** For example, `TokenService` explains why unknown users are checked
  against a dummy hash, and `RefreshTokenRepository` explains why the lookup takes a row lock.
- **Document known limitations at the code**, for example the strict reset of the failed-login
  counter in `AccountLockoutService`.
- **Change the documentation in the same commit as the code.** This rule is not checked
  automatically; reviewers should look for it.

### Current state

| Check (2026-11-13) | Result |
|---|---|
| Types with Javadoc | 142 / 142 |
| Public methods with Javadoc (excluding `@Override` and Lombok) | 137 / 137 |
| Modules with `package-info.java` | 7 / 7 |

### HTML Javadoc

```bash
mvn javadoc:javadoc
# open target/reports/apidocs/index.html
```

## Debugging

| Goal | How |
|---|---|
| See the SQL statements | Start with `--logging.level.org.hibernate.SQL=DEBUG` |
| See why a request was rejected by security | `--logging.level.org.springframework.security=DEBUG` |
| Follow one request | Send `X-Request-ID: my-test-1` and search the log for `[my-test-1]` |
| Read registration codes without SMTP | Keep `MAIL_ENABLED=false`; the code is logged at `DEBUG` by `MailService` |
| Try endpoints | Swagger UI at `/swagger-ui.html` in the `local` profile. Log in with `POST /oauth2/user/token`, then use **Authorize** with the access token |
| Start with an empty database | `docker rm -f design-db` and start MariaDB again; Flyway recreates the schema |
| Inspect a token | Decode the middle part of the JWT (Base64url); never paste real tokens into online tools |

## Workflow and releases

**Branches and pull requests**

1. Create a branch from `master`.
2. Make the change, including tests and documentation (see the definition of done below).
3. Open a pull request. CI must pass before it is merged.

**Versioning:** the project uses [Semantic Versioning](https://semver.org). Increase the major
version for incompatible API changes, the minor version for new features and the patch version
for fixes.

**Releasing a version**

1. Move the entries under `[Unreleased]` in `CHANGELOG.md` to a new version heading with the date.
2. Set the same version in `pom.xml` (`<version>`) and, if it is shown there, in the README.
3. Merge to `master` and create a Git tag with the version number, for example `2.1.0`.
4. Build the image with that tag: `docker build -t design-daas-backend:2.1.0 .`

## Definition of done

A change is ready to merge when:

- [ ] `mvn verify` passes locally and in CI (tests, ArchUnit, coverage gate)
- [ ] New behaviour is covered by unit and integration tests
- [ ] Javadoc, `docs/` and `CHANGELOG.md` are updated
- [ ] Schema changes come with a new migration, and the migration test passes
- [ ] No secrets, personal data or generated keys are committed
