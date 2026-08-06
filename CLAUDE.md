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

### 4. Separate operations from data

- Services return **sealed result types** (`SaveNoteResult` = `Saved` | `EmptyText` | `TextTooLong`) instead of throwing exceptions for expected outcomes. Exceptions are only for bugs and infrastructure failures.
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

## Architecture (enforced by ArchitectureTest)

```
com.example.demo.<feature>/
├── domain/        records + sealed types only; NO Spring/framework imports
├── application/   services operating on domain data
└── web/           controllers, WebSocket handlers; maps outcomes to views
```

- `..domain..` must not depend on Spring, Servlet APIs, or the `application`/`web` layers.
- All fields in `..domain..` must be final.
- No field injection anywhere — constructor injection only.

Run `./mvnw test -Dtest=ArchitectureTest` after adding packages; violations fail the build.

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
