# CLAUDE.md

Spring Boot 4 + Thymeleaf + htmx template (generated with ttcli). Java 25, Tailwind CSS 4 + daisyUI via Vite (bun as package manager), WebSocket support. This template practices **Data Oriented Programming (DOP)** — read the conventions below before writing any Java code.

## Commands

- `./mvnw verify` — full build: compiles with Error Prone + NullAway, runs tests (including ArchUnit rules)
- `./mvnw spring-boot:run` — run the app (http://localhost:8080)
- `bun run dev` — Vite dev server with live reload (run alongside `spring-boot:run`)
- `./mvnw test -Dtest=ArchitectureTest` — architecture rules only

## Data Oriented Programming conventions

This codebase follows the four DOP principles (see [Data Oriented Programming in Java](https://nejckorasa.github.io/posts/data-oriented-programming-in-java/) and [Inside Java's DOP v1.1](https://inside.java/2024/05/23/dop-v1-1-introduction/)). The `notes` feature is the reference implementation — copy its structure for every new feature.

### 1. Model data immutably and transparently

- Domain data is `record`s, never classes with getters/setters. No Lombok — records replace it.
- Wrap identifiers in dedicated records (`NoteId`), not bare `UUID`/`long`.
- Collections held by records are copied defensively or immutable (`List.copyOf`).

### 2. Model the data, the whole data, and nothing but the data

- Records carry no behavior beyond invariant checks and derived accessors. Business logic lives in services (`notes/application`), never inside the data.
- Form/request payloads are records. `@ConfigurationProperties` are records.

### 3. Make illegal states unrepresentable

- Enforce invariants in compact constructors (`Note` rejects blank/overlong text) so an invalid instance cannot exist.
- Nullness is part of the type system: every package is `@NullMarked` (JSpecify) and NullAway fails the build on violations. **Every new package needs a `package-info.java` with `@NullMarked`.** Where absence is genuinely part of the model, mark it explicitly with `@Nullable` — but prefer restructuring the data (e.g. a sealed hierarchy) over nullable members.
- Prefer distinct sealed variants over boolean flags or enum + nullable-fields combinations.

### Evolving records, and classes that can't quite be records

Conventions aligned with where Java's DOP support is heading ([carrier classes](https://mail.openjdk.org/pipermail/amber-spec-experts/2026-January/004307.html), `with` reconstruction — JEP 468). Following them costs nothing today and makes that future adoption mechanical:

- **Evolve a record by appending components at the end only** — never reorder or insert. Keep an explicit constructor with the old shape delegating to the canonical one with defaults. Reordering breaks every deconstruction pattern positionally and silently when component types coincide.
- **All invariants live in the compact (canonical) constructor; every other constructor or factory delegates to it.** One choke point means no invalid instance can exist through any path — and it is what will make `with`-style reconstruction safe when it arrives.
- **Do not hand-write `withX(...)` copy methods on records** unless a call site genuinely needs one; when you do, implement them via the canonical constructor, never by field-copying around it.
- **When a class can't be a record** (mutable, cached, or derived state; representation differs from API), still shape it like one: record-style accessors (`x()`, not `getX()`), one canonical constructor matching the full state description, `equals`/`hashCode`/`toString` over exactly that state. Such a class remains honest data today and becomes a carrier class by deleting boilerplate when they ship.
- For public API boundaries, prefer a **sealed interface with a private record implementation** (`public sealed interface Pair<T,U> permits PairImpl`) — consumers get pattern matching without coupling to the representation.

### 4. Separate operations from data

- Services return **sealed result types** (`SaveNoteResult` = `Saved` | `EmptyText` | `TextTooLong`) instead of throwing exceptions for expected outcomes. Exceptions are only for bugs and infrastructure failures.
- **Expected outcomes vs. infrastructure faults**: sealed results model outcomes the domain *expects* (validation failure, duplicate, not-found). Transient infrastructure faults (network, downstream outage) stay exceptions — handle them at the adapter with Spring Framework 7's core resilience annotations (`@EnableResilientMethods`, then `@Retryable`/`@ConcurrencyLimit` on the outbound method). Don't model retryable faults as sealed variants, and don't retry domain outcomes.
- **Outbound HTTP uses HTTP interface clients** (`@GetExchange` interfaces registered via `@ImportHttpServices`), returning records — never a raw `RestClient` call materializing `Map`s. This keeps the type-discipline rule intact at the outbound boundary: JSON binds to records at the edge.
- Callers handle results with **exhaustive `switch` expressions using record deconstruction**:

  ```java
  return switch (noteService.save(text)) {
      case Saved _ -> { notesBroadcaster.broadcastNotes(noteService.all()); yield "notes/form :: note-form"; }
      case EmptyText() -> formWithError(model, text, "Note text must not be empty.");
      case TextTooLong(int max, int actual) -> formWithError(model, text, "Limit is %d, got %d.".formatted(max, actual));
  };
  ```

- **Never write a `default ->` branch in a switch over a sealed type or enum.** A default silently disables the compiler's exhaustiveness check — the whole point is that adding a new variant breaks compilation at every call site until it is handled. This applies to test code too.
- Use unnamed patterns (`case Saved _`) when bindings are unused.

## Type discipline: parse, don't validate

Records and sealed types are evidence. Preserve evidence already established, create it by parsing raw input once at the boundary, and never fabricate it with a cast after throwing it away.

- **Raw input is only raw at a genuine trust boundary** — HTTP parameters, JSON, config, external systems, all living in `web/` or config classes. A raw value crosses inward only as the input of a single parsing operation that returns a domain type or a sealed failure (`NoteService.save(String)` → `SaveNoteResult`). Before typing a parameter as `String`/`Map`/`JsonNode`, answer: *which external boundary made this value raw?* No answer means use the domain type.
- **Possessing a domain value is proof.** `Note`'s compact constructor guarantees its invariants, so code holding a `Note` never re-checks them — re-validation downstream is not defensive, it destroys the meaning of the type.
- **Never widen a known value**: no `Object` or `Map<String, Object>` around known data, no stringly-typed signatures (`NoteId`, never a bare `UUID` or `String` id, in every layer), and never collapse a sealed result into a boolean, status code, or nullable — pass it along, or switch it exhaustively where it is consumed. *Enforced for `domain`/`application` method signatures by ArchitectureTest (`no_widened_signatures_in_inner_layers`).*
- **Never erase a type and cast it back.** `instanceof`-then-cast is replaced by pattern matching (*enforced: Error Prone `PatternMatchingInstanceof` fails the compile*). An unchecked cast is allowed only for an invariant the compiler cannot express, at the smallest possible scope, with a comment stating that invariant; in `domain`/`application` code it is a review blocker.
- **Use the strongest type the owner exports**: `HtmxRequest` over reading headers, `Locale`/`Instant` over their string forms, library types over local approximations.

Review triggers: `Object` parameters or returns outside a real boundary; sealed outcomes encoded as booleans/ints/nulls; casts following a widening our own code introduced; re-validation of constructor-guaranteed invariants.

## Architecture (enforced by ArchitectureTest)

```
com.example.demo.<feature>/
├── domain/        records + sealed types only; NO Spring/framework imports
├── application/   services operating on domain data + persistence ports
├── persistence/   JdbcClient adapters implementing the ports
└── web/           controllers, WebSocket handlers; maps outcomes to views
```

- `..domain..` must not depend on Spring, Servlet APIs, JDBC, or the `application`/`persistence`/`web` layers.
- All fields in `..domain..` must be final.
- No field injection anywhere — constructor injection only.

Run `./mvnw test -Dtest=ArchitectureTest` after adding packages; violations fail the build.

## Persistence

The database is a trust boundary, handled exactly like HTTP input:

- The `application` layer defines a small **port** (`NoteRepository`: domain types in, domain types out). Services depend on the port, never on JDBC — which keeps them unit-testable with a five-line in-memory fake (see `NoteServiceTest`).
- The `persistence` adapter implements the port with `JdbcClient` and explicit SQL. **A row becomes a domain record in exactly one place** — the adapter's row mapper (`JdbcNoteRepository.mapNote`). Nothing above the adapter ever sees a `ResultSet` or a `Map` of columns; the widened-signature ArchUnit rule covers `..persistence..` too.
- Deliberate choice: `JdbcClient`, **not Spring Data JDBC** — repositories via `ListCrudRepository` would need `@Id`/`@Version` on domain records (breaking domain framework-freedom) plus converter setup for `NoteId`. If you switch anyway, exempt `org.springframework.data.annotation..` in `ArchitectureTest` explicitly and use a `@Version` field so pre-assigned `NoteId`s insert correctly.
- Runtime is PostgreSQL (`application.properties`); tests run against embedded H2 (`src/test/resources/application.properties` — it shadows the main file, so repeat any needed keys there). Keep the SQL portable across both. Adapter changes get a `@JdbcTest` + `@Import(<adapter>.class)` slice test.
- Schema lives in `schema.sql` (fine for a template); switch to Flyway or Liquibase migrations in a real project. Ordering and filtering belong in SQL (`order by created_at desc`), not in Java streams.

## htmx + WebSocket conventions

- Each sealed result variant maps to its own htmx response: success and each failure render their own fragment (see `NoteController`). Fragments live in `templates/<feature>/`.
- Shared state changes are pushed to all browsers via WebSocket: render the fragment server-side with `SpringTemplateEngine` and broadcast it; the htmx `ws` extension applies it as an out-of-band swap on the element with the matching `id` (see `NotesBroadcaster`, registered in `WebSocketConfig`). The broadcast variant of a fragment sets `hx-swap-oob="true"`; the page-load variant must not.
- htmx core and the `ws` extension are webjars, loaded in `layout/main.html`.

## The example slice is disposable

The `notes` feature exists to demonstrate the conventions, not to ship. Keep it as the reference while the project has no real features yet; once the first real feature slice exists, delete the example and treat that feature as the reference instead:

1. Delete `src/main/java/com/example/demo/notes/`, `src/test/java/com/example/demo/notes/`, and `src/main/resources/templates/notes/`.
2. Remove the notes section from `index.html` and the `NoteService` dependency from `HomeController`; drop the `Clock` bean if nothing else uses it.
3. Update the "reference implementation" pointer in this file to the real feature.
4. `./mvnw verify` must stay green after removal — nothing else may depend on the example.

## GraalVM native image

`./mvnw -Pnative native:compile` produces a native binary (needs GraalVM JDK 25, gcc, zlib1g-dev). Two rules keep it working:

1. **Never add Groovy-based dependencies** (e.g. `thymeleaf-layout-dialect`) — Groovy breaks GraalVM native builds. Layouts use plain Thymeleaf fragment composition (`layout/main.html` fragment + `th:replace` with fragment arguments in pages) for exactly this reason.
2. **Register template-visible types in `NativeRuntimeHints`**: Thymeleaf resolves template expressions through SpEL reflection, invisible to Spring AOT. Any record or JDK type whose methods a template calls (`note.text()`, `notes.isEmpty()`, `#locale.toLanguageTag()`) must be registered there, or the page fails at render time in native only. When adding a template that touches new types, add hints for them.

## Build guardrails

- **Error Prone + NullAway** run on every compile (config in `pom.xml`, JVM exports in `.mvn/jvm.config`). NullAway violations are compile errors.
- `-Xlint:all` is enabled; keep the build warning-clean.
- Tests assert on result **values** (`assertThat(result).isEqualTo(new TextTooLong(280, 285))`) — records give you equality for free. Switch exhaustively in tests instead of `default`-ing to a failure.
