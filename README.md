# spring-htmx-dop-template

A Spring Boot + [Datastar](https://data-star.dev) template that practices **Data Oriented Programming** — enforced with compiler-level guardrails — on a deliberately collapsed stack: typed templates, zero-infrastructure persistence, realtime over plain HTTP, and **no Node toolchain**. Inspired by the integrated spirit of Convex/Lakebed and the consolidation spirit of Vite+, kept 100% Spring.

| | |
|---|---|
| Backend | Spring Boot 4.1, Java 25, virtual threads |
| Templates | **JTE** — compiled to Java classes at build time: typed params, no runtime reflection |
| Frontend | **Datastar** (one vendored 34 kB script — hypermedia + signals + SSE in one model), Tailwind CSS 4 **standalone binary**, shadcn-style components via [Basecoat](https://basecoatui.com) (plain CSS webjar) |
| Database | **SQLite** — one file, zero servers ([outgrowing it](#outgrowing-sqlite) is a two-line swap) |
| Guardrails | Error Prone + NullAway (JSpecify), ArchUnit, Spring Modulith, `-Xlint:all` |

The DOP principles ([blog](https://nejckorasa.github.io/posts/data-oriented-programming-in-java/), [Inside Java](https://inside.java/2024/05/23/dop-v1-1-introduction/)): model data immutably as records; the whole data and nothing but the data; make illegal states unrepresentable; separate operations from data with sealed result types and exhaustive switches. In v2 the type discipline extends to the **view layer**: a template is a typed function over records — a typo in `note.text()` fails compilation, not a request.

## Quick start

```bash
gh repo create my-app --template nkhokhla/spring-htmx-dop-template --private --clone
cd my-app
export JAVA_HOME=/path/to/jdk-25
./mvnw verify              # full build with all guardrails (downloads Tailwind standalone on first run)
./mvnw spring-boot:run     # http://localhost:8080 — that's it, the database is a file
```

No Node, no npm, no Docker, no database server. Open two browser tabs and add a note — it appears in both (SSE), and it's still there after a restart (SQLite).

Dev loop: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (JTE hot-reloads templates from source) plus `tools/tailwind.sh -i src/main/css/application.css -o target/classes/static/css/application.css --watch` when editing styles.

## The example slice

```
notes/
├── domain/        Note, NoteId (records), SaveNoteResult (sealed: Saved | EmptyText | TextTooLong)
├── application/   NoteService (sealed results) + NoteRepository port (domain types in/out)
├── persistence/   JdbcNoteRepository — JdbcClient + SQL; rows become records in ONE row mapper
└── web/           NoteController — exhaustive switch → element/signal patches per outcome
                   NotesEventStream — renders the list template, patches it into all tabs
                   SaveNoteSignals — the typed record the Datastar signals parse into
```

Templates live in `src/main/jte/` with declared `@param` types — the compiler checks them against your records. The slice is disposable: copy its structure for your first real feature, then delete it (steps in [CLAUDE.md](CLAUDE.md)).

## Datastar in one paragraph

The page binds the input to a `$text` signal (`data-bind:text`) and submits via `data-on:submit="@post('/notes')"` — Datastar sends the signals as JSON and expects an SSE stream back. The server answers with **signal patches** for ephemeral view state (clear the input, set `$error` — rendered by `data-show`/`data-text`, so there is *no form template at all*) and broadcasts **element patches** for domain data (the JTE-rendered list, morphed by id into every tab connected via `data-init="@get('/notes/events')"`). One mechanism for request/response *and* realtime; the [Gadnex jte-datastar starter](https://github.com/Gadnex/jte-datastar-spring-boot-starter) renders JTE templates straight into patch events (`datastar.patchElements(emitters).template("notes/list").attribute("notes", notes).emit()`). It is a small, young project — if it ever goes unmaintained, its ~10 classes are trivially inlined.

## Outgrowing SQLite

SQLite is the default because a template should run on clone — and it is a real production database for a large class of apps. When you outgrow it, the port/adapter design makes the move a **two-line swap** plus SQL dialect touches:

1. `pom.xml`: replace `org.xerial:sqlite-jdbc` with `com.mysql:mysql-connector-j` (or `org.postgresql:postgresql`).
2. `application.properties`: point `spring.datasource.url` at the server.
3. Adjust `schema.sql` types and the adapter's storage conventions (SQLite stores ids as UUID text and timestamps as ISO-8601 text; MySQL wants `char(36)` + `datetime(6)`, PostgreSQL has native `uuid` + `timestamptz`).

Nothing above the adapter changes — that is the point of parsing rows into records in exactly one place. The [`with-jdbc` branch](https://github.com/nkhokhla/spring-htmx-dop-template/tree/with-jdbc) is a verified MySQL implementation of the same port (from the v1 stack) if you want a working reference.

## Working with AI agents

- **`CLAUDE.md` is the contract** — DOP rules, type discipline, module boundaries, all stated in enforceable terms.
- **The example slice is the few-shot example** — agents pattern-match on code; the slice keeps their first feature in shape.
- **`./mvnw verify` is the completion gate** — NullAway, ArchUnit, Modulith, exhaustive switches, and now *template type-checking* turn drift into compile errors the agent fixes itself. In v2 even a template referencing a renamed record component fails the build.
- Ask for one vertical slice per task ("add a `tags` feature like `notes`").

## Commands

| Command | What it does |
|---|---|
| `./mvnw verify` | Full build: Error Prone + NullAway, JTE compilation, Tailwind, all tests |
| `./mvnw spring-boot:run` | Run (precompiled templates) |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` | Run with JTE hot reload |
| `tools/tailwind.sh … --watch` | Rebuild CSS on change during styling work |
| `./mvnw test -Dtest=ArchitectureTest` / `-Dtest=ModularityTest` | Architecture / module rules only |
| `./mvnw -Pnative native:compile` | GraalVM native image |

Requires JDK 25 and bash+curl (for the self-downloading Tailwind binary; on Windows use WSL or install Tailwind standalone manually as `tools/tailwindcss-<version>-<platform>`).

## Native image & AOT

The v2 stack is dramatically friendlier to GraalVM than template engines with runtime reflection: JTE templates are precompiled classes, so **this branch has no hand-written reflection hints at all** (v1's `NativeRuntimeHints` is gone — the only metadata is emitted automatically per template by JTE's `NativeResourcesExtension`, already wired into the build). Verified end to end: 0.4s startup, all sealed outcomes, SSE, SQLite persistence. `./mvnw -Pnative native:compile` needs a GraalVM JDK 25, `gcc`, and `zlib1g-dev`. For a cheaper startup boost on the JVM, use the JDK 25 AOT cache (Project Leyden):

```bash
./mvnw package
java -Djarmode=tools -jar target/demo-0.0.1-SNAPSHOT.jar extract --destination target/aot
cd target/aot && java -XX:AOTCacheOutput=demo.aot -Dspring.context.exit=onRefresh -jar demo-0.0.1-SNAPSHOT.jar
java -XX:AOTCache=demo.aot -jar demo-0.0.1-SNAPSHOT.jar
```

---

Earlier stacks live in git history: v1 (Thymeleaf, htmx, Vite/bun, WebSocket, daisyUI) and the `with-jdbc` MySQL branch. DOP references: [jitterted/tdd-game](https://github.com/jitterted/tdd-game), [Suigi/event-sourced-tic-tac-toe](https://github.com/Suigi/event-sourced-tic-tac-toe), [zodac/diurnal](https://github.com/zodac/diurnal).
