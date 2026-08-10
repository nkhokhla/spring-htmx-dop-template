# Recipe: add SQL persistence (MySQL reference)

Replaces the in-memory store with a SQL database behind a port/adapter, keeping every template guardrail intact. A complete, verified MySQL implementation of this recipe lives on the [`with-jdbc` branch](https://github.com/nkhokhla/spring-htmx-dop-template/tree/with-jdbc) — diff it against `main` to see exactly what changes. An agent can follow the steps below directly. PostgreSQL instead is a two-line swap (noted per step).

## Design (read first)

- The database is a **trust boundary**, treated like HTTP input: a row becomes a domain record in exactly one place — the adapter's row mapper. Nothing above the adapter sees a `ResultSet` or column `Map`.
- We use **`JdbcClient` with explicit SQL, not Spring Data JDBC**. Spring Data repositories put `@Id`/`@Version` on domain records, which breaks the ArchUnit rule that `domain` is framework-free, and need converter setup for wrapped ids like `NoteId`. See the trade-off note at the end if you want Spring Data anyway.
- Services depend on a small **port interface** (domain types in/out) declared in `application`; the `JdbcClient` adapter lives in a new `persistence` package. Unit tests fake the port in five lines — no database, no mocks framework.
- **Storage conventions live in the adapter, nowhere else**: MySQL has no `uuid` or `timestamp with time zone` types, so ids are stored as `char(36)` UUID text and timestamps as `datetime(6)` holding UTC wall-clock time, converted via `LocalDateTime.ofInstant(..., ZoneOffset.UTC)` in the mapper. (PostgreSQL: use native `uuid` and `timestamp with time zone`, bind `UUID`/`OffsetDateTime` directly.)

## Steps

1. **Dependencies** (pom.xml): add `spring-boot-starter-jdbc`, `com.mysql:mysql-connector-j` (runtime scope), `com.h2database:h2` (test scope), `spring-boot-starter-jdbc-test` (test scope). *(PostgreSQL: `org.postgresql:postgresql` instead of the MySQL driver.)*
2. **Schema**: `src/main/resources/schema.sql` — `char(36)` primary keys, `datetime(6)` timestamps, constraints mirroring record invariants (e.g. `varchar(280) not null`). Set `spring.sql.init.mode=always`. Switch to Flyway/Liquibase in a real project.
3. **Datasource** (`application.properties`): `spring.datasource.url=jdbc:mysql://localhost:3306/<db>` plus credentials.
4. **Test datasource**: create `src/test/resources/application.properties` pointing at embedded H2 in MySQL mode (`jdbc:h2:mem:...;DB_CLOSE_DELAY=-1;MODE=MySQL`). Warning: this file *shadows* the main one on the test classpath — repeat every key tests need (`vite.mode=build` etc.).
5. **Port** in `<feature>/application`: an interface taking/returning domain records only (e.g. `save(Note)`, `List<Note> all()`). Ordering and filtering belong in SQL, so the port's contract states them ("newest first").
6. **Adapter** in `<feature>/persistence` (new package — needs `package-info.java` with `@NullMarked`): `@Repository` class implementing the port with `JdbcClient`; one private static row-mapper method is the single row→record parse point, and it owns the storage conventions above.
7. **Rewire the service** to the port; delete the in-memory map.
8. **ArchUnit** (`ArchitectureTest`): add `java.sql..` and `..persistence..` to the packages `domain` must not depend on; add `..persistence..` to the widened-signature rule's packages.
9. **Tests**: keep the service test pure — an in-memory fake of the port. Add a `@JdbcTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)` + `@Import(<Adapter>.class)` slice test asserting a round-trip and the SQL ordering. `Replace.NONE` matters: without it the slice swaps in a plain-H2 datasource that ignores the MySQL-mode URL from step 4.
10. **Run it**: start MySQL (Docker: `docker run -d -p 3306:3306 -e MYSQL_DATABASE=<db> -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:9`, or a local install), then `./mvnw spring-boot:run`. Verify a saved note survives an application restart.
11. **Update CLAUDE.md**: copy the "Persistence" section from the `with-jdbc` branch so future work follows the same rules.

With Docker available, prefer Testcontainers over H2 for the adapter test: `spring-boot-testcontainers` + `org.testcontainers:mysql` + `@ServiceConnection` — then the slice test runs against the real database and step 4's portability constraint disappears.

## If you prefer Spring Data JDBC instead

Legitimate choice (records map well to immutable aggregates; Boot 4 generates repository implementations at build time). Three things change, and they must be deliberate: exempt `org.springframework.data.annotation..` from the domain ArchUnit ban (annotations only — nothing else); add a `@Version` component so aggregates with pre-assigned ids insert as new instead of failing as updates; register converters for wrapped id types (`NoteId` ↔ `UUID`) via a `JdbcCustomConversions` bean. Do not silently widen the ArchUnit exemption beyond the annotation package.
