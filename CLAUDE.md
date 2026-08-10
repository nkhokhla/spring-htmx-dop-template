# CLAUDE.md

Spring Boot 4 + JTE + htmx template (v2 stack). Java 25, virtual threads, SQLite persistence, SSE realtime, Tailwind CSS 4 standalone binary + shadcn-style Basecoat components (webjar) — no Node toolchain. This template practices **Data Oriented Programming (DOP)** — read the conventions below before writing any Java code.

## Commands

- `./mvnw verify` — full build: Error Prone + NullAway, JTE template compilation, Tailwind CSS, all tests
- `./mvnw spring-boot:run` — run the app (http://localhost:8080); the SQLite database is the `demo.db` file
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` — dev mode: JTE hot-reloads templates from `src/main/jte`
- `tools/tailwind.sh -i src/main/css/application.css -o target/classes/static/css/application.css --watch` — CSS watch during styling work
- `./mvnw test -Dtest=ArchitectureTest` / `-Dtest=ModularityTest` — architecture / module rules only

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
      case Saved _ -> { notesEventStream.broadcastNotes(noteService.all()); yield "notes/form"; }
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
└── web/           controllers, SSE streams; maps outcomes to views
```

- `..domain..` must not depend on Spring, Servlet APIs, JDBC, or the `application`/`persistence`/`web` layers.
- All fields in `..domain..` must be final.
- No field injection anywhere — constructor injection only.

Run `./mvnw test -Dtest=ArchitectureTest` after adding packages; violations fail the build.

## Modules (enforced by ModularityTest)

Each top-level feature package (`notes`, …) is a **Spring Modulith module** (test-scoped dependency only — no runtime weight):

- Nested packages (`domain`, `application`, `web`, …) are module-private. Another module may only use types placed in the module's base package. `ModularityTest.modulesRespectTheirBoundaries()` fails the build on leaks the compiler allows.
- **Cross-module communication happens via record events**, published with `ApplicationEventPublisher` — never by injecting another module's service. The event record goes in the publisher's base package (its public API). If async/transactional delivery with an outbox is needed, add the runtime `spring-modulith-starter-core` + `@ApplicationModuleListener` then.
- **Package-private by default.** A type becomes `public` only when another package genuinely needs it. Controllers, configs, and adapters are package-private (see `NoteController`, `WebSocketConfig`).
- `ModularityTest.writeArchitectureDocumentation()` regenerates C4/PlantUML module diagrams into `target/spring-modulith-docs` on every build — always-current architecture docs.

## Persistence (SQLite)

The database is a trust boundary, handled exactly like HTTP input:

- The `application` layer defines a small **port** (`NoteRepository`: domain types in, domain types out). Services depend on the port — unit-testable with a five-line in-memory fake (see `NoteServiceTest`).
- The `persistence` adapter implements the port with `JdbcClient` and explicit SQL. **A row becomes a domain record in exactly one place** — the adapter's row mapper. Storage conventions live there and nowhere else: SQLite stores ids as UUID text and timestamps as ISO-8601 UTC text (which sorts chronologically as plain text).
- SQLite is the default because it needs zero infrastructure and is a legitimate production database. **Outgrowing it is a two-line swap** (driver dependency + datasource URL) plus dialect touches in `schema.sql` and the adapter — see README "Outgrowing SQLite"; the `with-jdbc` branch holds a verified MySQL reference.
- Tests run against real SQLite (shared in-memory mode, `src/test/resources/application.properties` — it shadows the main file, repeat any keys tests need). Adapter changes get a `@JdbcTest` + `Replace.NONE` + `@Import(<adapter>.class)` slice test.
- Schema lives in `schema.sql`; switch to Flyway/Liquibase in a real project. Ordering and filtering belong in SQL, not Java streams.

## JTE templates + htmx + SSE conventions

- Templates live in `src/main/jte/` and are **compiled to Java classes at build time** (jte-maven-plugin): every template declares typed `@param`s and the compiler checks them against your records — a template referencing a renamed component fails the build. There is NO template reflection: no SpEL, no runtime hints, nothing to register for native.
- Each sealed result variant maps to its own htmx response: success and each failure render their own template (see `NoteController`). One template per fragment (`notes/form.jte`, `notes/list.jte`); layout via `layout/main.jte` taking `gg.jte.Content` parameters.
- Shared state changes are pushed to all browsers over **SSE**: `NotesEventStream` renders the list template with the injected `gg.jte.TemplateEngine` and sends it as a named event; the htmx `sse` extension (`hx-ext="sse"`, `sse-connect`, `sse-swap` + `hx-swap="outerHTML"`) replaces the subscribed element. Same template for initial render and broadcasts — no out-of-band variants needed. SSE is plain HTTP: no WebSocket config, no origin handling.
- Styling: shadcn-style component classes come from the Basecoat webjar (`btn`, `btn-primary`, `input`, `card`, …); layout utilities from Tailwind (standalone binary, scans `src/main/jte` via `@source`). No Node, no npm — don't add them back.

## The example slice is disposable

The `notes` feature exists to demonstrate the conventions, not to ship. Keep it as the reference while the project has no real features yet; once the first real feature slice exists, delete the example and treat that feature as the reference instead:

1. Delete `src/main/java/com/example/demo/notes/`, `src/test/java/com/example/demo/notes/`, and `src/main/jte/notes/`.
2. Remove the notes section from `index.jte` and give your first real feature's controller the `GET /` mapping (it lives in `NoteController` now); drop the `Clock` bean and the `note` table in `schema.sql` if nothing else uses them.
3. Update the "reference implementation" pointer in this file to the real feature.
4. `./mvnw verify` must stay green after removal — nothing else may depend on the example (ModularityTest and ArchitectureTest will tell you if it does).

## GraalVM native image

`./mvnw -Pnative native:compile` produces a native binary (needs GraalVM JDK 25, gcc, zlib1g-dev). The v2 stack needs **no reflection hints**: JTE templates are precompiled classes (no SpEL, no template reflection), so there is no `NativeRuntimeHints` and nothing to register when adding templates. Keep it that way:

1. **Never add Groovy-based dependencies** — Groovy breaks GraalVM native builds.
2. **Prefer reflection-free libraries**; if a dependency does need reflection in native, register hints in a module-owned `RuntimeHintsRegistrar` rather than a root-level catch-all.

## Build guardrails

- **Error Prone + NullAway** run on every compile (config in `pom.xml`, JVM exports in `.mvn/jvm.config`). NullAway violations are compile errors.
- `-Xlint:all` is enabled; keep the build warning-clean.
- Tests assert on result **values** (`assertThat(result).isEqualTo(new TextTooLong(280, 285))`) — records give you equality for free. Switch exhaustively in tests instead of `default`-ing to a failure.
