# CLAUDE.md

This file is the contract between us (the humans directing the work) and you (the agent writing the code). It ships with the template: every project cloned from here inherits it, and the code you write becomes the example the next agent pattern-matches on — so write like the reference slice, because your output is tomorrow's reference.

**The thing:** a Spring Boot 4 template — Java 25, virtual threads, JTE templates compiled to Java classes, Datastar over SSE for request/response *and* realtime, SQLite, Tailwind CSS 4 standalone binary + Basecoat component classes (webjar). The codebase practices **Data Oriented Programming** ([DOP in Java](https://nejckorasa.github.io/posts/data-oriented-programming-in-java/), [Inside Java's DOP v1.1](https://inside.java/2024/05/23/dop-v1-1-introduction/)) stated in terms the build enforces.

Two properties are the product and are never traded away:

1. **Zero toolchain, zero infrastructure.** No Node, no npm, no Docker, no database server, no Groovy, no runtime template reflection — the native image builds with zero hand-written hints. Clone, `./mvnw verify`, run. A change that adds a toolchain, a server process, or a reflection hint is breaking the product: say so loudly and get approval before making it.
2. **The build is the reviewer.** The conventions below are guardrails, not prose. When a guardrail blocks you, the code is wrong — fix the code. Reaching green by suppressing a warning, loosening a rule, adding a `default ->` to a sealed switch, or widening a type is never the fix.

**Done means:** `./mvnw verify` is green and the change could be mistaken for the reference slice. For a new feature, walk the checklist in "Anatomy of a slice" and say which entries apply.

## Commands

- `./mvnw verify` — full build: Error Prone + NullAway, JTE template compilation, Tailwind CSS, all tests
- `./mvnw spring-boot:run` — run the app (http://localhost:8080); the SQLite database is the `demo.db` file
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` — dev mode: JTE hot-reloads templates from `src/main/jte`
- `tools/tailwind.sh -i src/main/css/application.css -o target/classes/static/css/application.css --watch` — CSS watch during styling work
- `./mvnw test -Dtest=ArchitectureTest` / `-Dtest=ModularityTest` — architecture / module rules only
- `./mvnw -Pnative native:compile` — GraalVM native image (needs GraalVM JDK 25, gcc, zlib1g-dev)

## The words we work with

- **evidence** — a value whose type proves its invariants (`Note` cannot be blank, `NoteId` cannot be an arbitrary string). Evidence is created in exactly one place, and holding it is proof.
- **boundary** — where raw data enters: HTTP parameters, Datastar signals JSON, database rows, config, external APIs. Raw values are legitimate only at a boundary.
- **widen** — turn evidence back into raw: `Object`/`Map` around known data, a bare `UUID`/`String` where an id record exists, a sealed result collapsed to a boolean, status code, or null.
- **outcome** — a result the domain expects (`Saved`, `EmptyText`, `TextTooLong`), modeled as a sealed type. **fault** — an infrastructure failure (network, downstream outage); faults stay exceptions.
- **slice** — one feature package with `domain/ application/ persistence/ web/`; a Spring Modulith module whose insides are private.
- **the reference slice** — `notes`: the worked example every rule below points into. Copy its structure for every feature. It is disposable — see the last section.
- **guardrail** — a build check that turns a convention into a compile/test failure: Error Prone + NullAway (config in `pom.xml` + `.mvn/jvm.config`), the JTE compiler, ArchitectureTest, ModularityTest, `-Xlint:all` kept warning-clean.

## Evidence over trust (parse, don't validate)

- Raw crosses a boundary exactly once, as the input of a single parse that returns evidence or an outcome (`NoteService.save(String)` → `SaveNoteResult`). Before typing a parameter `String`/`Map`/`JsonNode`, name the boundary that made it raw; no answer means use the domain type.
- Possession is proof: code holding a `Note` never re-checks its invariants — re-validation downstream destroys the meaning of the type.
- Widening destroys evidence, and someone downstream always pays to fabricate it back — a cast or a re-validation. Carry the domain type through every layer; switch a sealed result where it is consumed. ArchitectureTest (`no_widened_signatures_in_inner_layers`) rejects widened signatures outside `web/`.
- Pattern matching replaces `instanceof`-then-cast (Error Prone `PatternMatchingInstanceof` fails the compile). An unchecked cast is only for an invariant the compiler cannot express — smallest possible scope, comment stating the invariant, and a review blocker in `domain`/`application`.
- Use the strongest type the owner exports: `Locale`/`Instant` over their string forms, library types over local approximations.

## Outcomes are data, faults are exceptions

- Services return sealed results (`SaveNoteResult` = `Saved` | `EmptyText` | `TextTooLong`) for outcomes the domain expects — validation failure, duplicate, not-found. Exceptions are only for bugs and faults.
- Consume outcomes with an exhaustive `switch` + record deconstruction, in production and test code alike; use unnamed patterns (`case Saved _`) when bindings are unused. The canonical example is `NoteController.save(...)` in the reference slice.
- In tests, assert on outcome values — `assertThat(result).isEqualTo(new TextTooLong(280, 285))` — record equality is free.
- Faults are handled at the adapter with Spring Framework 7 resilience annotations (`@EnableResilientMethods`, then `@Retryable`/`@ConcurrencyLimit` on the outbound method). A fault is not a sealed variant; an outcome is not retried.
- Outbound HTTP uses HTTP interface clients (`@GetExchange` interfaces registered via `@ImportHttpServices`) returning records — JSON binds to evidence at the edge, same as inbound.

## Anatomy of a slice

The reference slice is the map — mirror `notes/` rather than reading a description of it here. The structural rules live where they are enforced: ArchitectureTest (framework-free immutable domain, no widened inner-layer signatures, constructor injection) and ModularityTest (nested packages are module-private; another module may only use types from a slice's base package). The conventions the tests cannot see:

- Cross-module communication is record events via `ApplicationEventPublisher` — the event record lives in the publisher's base package. Need async/transactional delivery with an outbox? Add runtime `spring-modulith-starter-core` + `@ApplicationModuleListener` then, not before.
- Package-private by default; a type becomes `public` only when another package genuinely needs it (controllers, configs, adapters stay package-private).

**A new slice touches all of:** table in `schema.sql` · domain records + sealed outcome · port + service in `application` · adapter with its one row mapper in `persistence` · controller + signals record (+ event stream if realtime) in `web` · JTE template(s) · a `package-info.java` with `@NullMarked` in **every** new package (nullness is part of the type system; NullAway violations fail the compile) · tests — service against an in-memory fake, adapter as a `@JdbcTest` slice. Run ArchitectureTest/ModularityTest after adding packages.

## Records in practice

- Domain data is records — no Lombok, no getter/setter classes. Identifiers get wrapper records (`NoteId`), never bare `UUID`/`long`. Form/request payloads and `@ConfigurationProperties` are records too.
- Records carry no behavior beyond invariant checks and derived accessors; logic lives in services.
- All invariants live in the compact (canonical) constructor; every other constructor or factory delegates to it — one choke point, so no invalid instance exists through any path. Collections held by records are immutable (`List.copyOf`).
- Absence: prefer a sealed hierarchy over a nullable member; where absence is genuinely part of the model, mark it `@Nullable`. Prefer distinct sealed variants over boolean flags or enum + nullable-field combinations.
- Evolve a record by appending components at the end only, keeping an old-shape constructor that delegates with defaults — reordering breaks deconstruction patterns positionally and silently. Hand-write `withX(...)` only when a call site needs it, implemented via the canonical constructor. (Aligned with where Java is heading: [carrier classes](https://mail.openjdk.org/pipermail/amber-spec-experts/2026-January/004307.html), JEP 468 `with` reconstruction.)
- A class that can't be a record (mutable/cached state, representation ≠ API) is still shaped like one: `x()` accessors, one canonical constructor over the full state, `equals`/`hashCode`/`toString` over exactly that state. For public API boundaries, prefer a sealed interface with a private record implementation — pattern matching without coupling to the representation.

## Persistence (SQLite)

The database is a boundary — rows are raw, exactly like HTTP input:

- The port (`NoteRepository`) speaks domain types in and out; services depend on the port and unit-test against a five-line in-memory fake (`NoteServiceTest`).
- The adapter uses `JdbcClient` + explicit SQL. A row becomes a record in exactly one place — the row mapper — and the storage conventions are documented there and nowhere else (read `JdbcNoteRepository` before persisting a new type).
- Ordering and filtering happen in SQL, not Java streams. Schema lives in `schema.sql`; switch to Flyway/Liquibase in a real project.
- Tests run against real SQLite (shared in-memory mode). `src/test/resources/application.properties` shadows the main file — repeat any keys tests need. Adapter changes get a `@JdbcTest` + `Replace.NONE` + `@Import(<adapter>.class)` slice test.
- SQLite is a legitimate production database and needs zero infrastructure. Outgrowing it is a two-line swap plus dialect touches — see README "Outgrowing SQLite"; the `with-jdbc` branch is a verified MySQL reference.

## Web (JTE + Datastar)

- Templates live in `src/main/jte/` and compile to Java classes at build time with typed `@param`s — renaming a record component breaks the referencing template at compile time. No SpEL, no runtime reflection, nothing to register for native (JTE's `NativeResourcesExtension` covers the generated classes).
- Two kinds of state, two kinds of patch, both server-authoritative:
  - *Domain data* (collections, records) is server-rendered JTE pushed as **element patches**: `datastar.patchElements(emitters).template("notes/list").attribute("notes", notes).emit()` (Gadnex starter). Datastar morphs the element with the matching `id` in every connected tab. One template serves initial render and patches — never a second variant.
  - *Ephemeral view state* (input contents, error text, loading flags) lives in **signals** patched back to the caller: `datastar.patchSignals(emitter).signal("error", …)`, rendered declaratively with `data-show`/`data-text` — no template needed for it.
- Signals arriving at the server are a boundary: `@post` sends them as JSON; bind them to a small record (`SaveNoteSignals`) — the parse-once point — and annotate the controller with `@RegisterReflectionForBinding(<SignalsRecord>.class)` for native.
- POST handlers return an `SseEmitter`: emit patches per outcome, then `complete()`. The long-lived stream plumbing (`data-init="@get('…/events')"`) lives in `NotesEventStream` — extend it rather than re-inventing it.
- The Datastar client is one vendored, version-pinned script (`static/js/datastar.js`). Attribute syntax is v1: `data-bind:text`, `data-on:submit="@post('/notes')"` (submit's default is auto-prevented), `data-signals`, `data-init`.
- Styling: Basecoat component classes (`btn`, `btn-primary`, `input`, `card`, …) + Tailwind layout utilities (standalone binary scans `src/main/jte` via `@source`).

## Native image

The stack needs no reflection hints — keep it that way. Reflection-free libraries first; if a dependency truly needs reflection in native, register hints in a module-owned `RuntimeHintsRegistrar`, never a root-level catch-all. Groovy-based dependencies break GraalVM native builds — a hard no.

## The reference slice is disposable

The `notes` feature exists to demonstrate the conventions, not to ship. Keep it as the reference while the project has no real features; once the first real slice exists, that becomes the reference and the example goes:

1. Delete `src/main/java/com/example/demo/notes/`, `src/test/java/com/example/demo/notes/`, and `src/main/jte/notes/`.
2. Remove the notes section from `index.jte` and give your first real feature's controller the `GET /` mapping (it lives in `NoteController` now); drop the `Clock` bean and the `note` table in `schema.sql` if nothing else uses them.
3. Update the "reference slice" pointer in this file to the real feature.
4. `./mvnw verify` must stay green after removal — nothing else may depend on the example (ModularityTest and ArchitectureTest will tell you if it does).
