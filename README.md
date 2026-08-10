# spring-htmx-dop-template

A Spring Boot + htmx project template that practices **Data Oriented Programming** — and enforces it with compiler-level guardrails, so the conventions survive contact with real development (human or AI).

| | |
|---|---|
| Backend | Spring Boot 4.1, Java 25, WebSockets |
| Frontend | Thymeleaf + htmx (with `ws` extension), Tailwind CSS 4 + daisyUI, Vite, bun |
| Guardrails | Error Prone + NullAway (JSpecify `@NullMarked`), ArchUnit, `-Xlint:all` |

Based on the principles in [Data Oriented Programming in Java](https://nejckorasa.github.io/posts/data-oriented-programming-in-java/) and [Inside Java's DOP v1.1](https://inside.java/2024/05/23/dop-v1-1-introduction/), with conventions aligned to the [next DOP feature arc (carrier classes)](https://mail.openjdk.org/pipermail/amber-spec-experts/2026-January/004307.html):

1. **Model data immutably and transparently** — records everywhere, no Lombok
2. **Model the data, the whole data, and nothing but the data** — behavior lives in services, not in the data
3. **Make illegal states unrepresentable** — invariants in compact constructors, nullness checked at compile time
4. **Separate operations from data** — services return sealed result types; callers switch exhaustively, never `default ->`

## Quick start

```bash
gh repo create my-app --template nkhokhla/spring-htmx-dop-template --private --clone
cd my-app
export JAVA_HOME=/path/to/jdk-25
./mvnw verify          # full build with all guardrails
./mvnw spring-boot:run # http://localhost:8080
bun run dev            # in a second terminal: Vite live reload
```

Open http://localhost:8080 in **two browser tabs** and add a note — it appears in both. That round-trip (form → sealed result → exhaustive switch → htmx fragment + WebSocket broadcast) is the whole template in one interaction.

## The example slice

`com.example.demo.notes` is a small vertical slice that exists to demonstrate the conventions:

```
notes/
├── domain/        Note, NoteId (records), SaveNoteResult (sealed: Saved | EmptyText | TextTooLong)
├── application/   NoteService — operations on the data, returns sealed results
└── web/           NoteController — exhaustive switch → one htmx fragment per outcome
                   NotesBroadcaster — renders fragments server-side, pushes via WebSocket (hx-swap-oob)
```

Try adding a fourth variant to `SaveNoteResult`: the build breaks at every switch until you handle it. That compile-time exhaustiveness is the core of the approach — no default branches, no forgotten cases.

**The slice is disposable.** Build your first real feature by copying its structure, then delete it (steps in [CLAUDE.md](CLAUDE.md)).

**Need a database?** The store is in-memory on purpose — `main` stays clone-and-run with zero infrastructure. When you need PostgreSQL, follow [docs/add-persistence.md](docs/add-persistence.md) (agent-executable recipe); a complete verified implementation lives on the [`with-jdbc` branch](https://github.com/nkhokhla/spring-htmx-dop-template/tree/with-jdbc).

## Suggested workflow for a new project

1. **Create from template and rename** — `com.example.demo` → your package, `demo` → your artifact id (IDE refactor handles the packages; `pom.xml` and `package.json` by hand).
2. **Get familiar** — run the app, break the exhaustiveness on purpose, skim `CLAUDE.md` next to the notes slice. Half an hour here pays for itself.
3. **Build features as slices** — each feature gets its own `domain/application/web` triple. ArchUnit fails the build if domain code touches the framework.
4. **Delete the example slice** once the first real feature exists — it is the reference implementation only until you have a real one.
5. **Keep `./mvnw verify` as the definition of done** — it runs Error Prone, NullAway, ArchUnit, and the tests in one command.

## Working with AI agents

The template is set up so agents inherit the conventions instead of needing them re-explained:

- **`CLAUDE.md` is the contract.** Claude Code (and most agent harnesses) load it automatically. It states the DOP rules, the architecture, and the htmx/WebSocket patterns in enforceable terms ("never `default ->` over a sealed type") rather than aspirations.
- **The example slice is the few-shot example.** Agents pattern-match on existing code far more reliably than on prose. Keeping the slice until your first real feature exists means an agent's first feature lands in the right shape; after that, your real code takes over as the pattern.
- **The guardrails are the reviewer.** Point agents at `./mvnw verify` as the completion gate: NullAway, ArchUnit, and exhaustive switches turn convention drift into compile errors the agent must fix itself, rather than something you catch in review.
- Ask for one vertical slice per task ("add a `tags` feature like `notes`") rather than cross-cutting changes — the architecture maps cleanly onto that request shape.

## Commands

| Command | What it does |
|---|---|
| `./mvnw verify` | Full build: Error Prone + NullAway, tests, ArchUnit, Vite production build |
| `./mvnw spring-boot:run` | Run the app |
| `bun run dev` | Vite dev server with live reload |
| `./mvnw test -Dtest=ArchitectureTest` | Architecture rules only |
| `./mvnw -Pnative native:compile` | GraalVM native image (see below) |

Requires JDK 25. Bun is installed locally by the Maven build; installing [bun](https://bun.sh) globally is only needed for `bun run dev`.

## Faster JVM startup: AOT cache (Project Leyden)

Before reaching for native image, there is a cheaper option: the JDK 25 AOT cache. No reflection hints, no extra build plugins, no native toolchain — three commands against the regular jar (measured here: ~30% faster startup):

```bash
./mvnw package
java -Djarmode=tools -jar target/demo-0.0.1-SNAPSHOT.jar extract --destination target/aot
cd target/aot && java -XX:AOTCacheOutput=demo.aot -Dspring.context.exit=onRefresh -jar demo-0.0.1-SNAPSHOT.jar
java -XX:AOTCache=demo.aot -jar demo-0.0.1-SNAPSHOT.jar
```

The training run (third command) starts the context, records the class/object profile, writes the cache, and exits. Ship the extracted directory plus the `.aot` file together. Use this when native's build cost or hint maintenance isn't worth it; use native when you need ~0.1s cold starts or minimal memory.

## GraalVM native image

The template compiles to a native binary (verified end to end: pages, all sealed outcomes, WebSocket broadcasts) and starts in ~0.1s:

```bash
./mvnw -Pnative native:compile
./target/demo
```

Requires a GraalVM JDK 25 as `JAVA_HOME` plus `gcc` and `zlib1g-dev` (`sudo apt install build-essential zlib1g-dev` on Debian/Ubuntu).

Two things keep native working here — preserve them as the project grows:

- **No Groovy on the classpath.** This template deliberately uses plain Thymeleaf fragment composition for layouts instead of `thymeleaf-layout-dialect`: the dialect is written in Groovy, and Groovy is broken on current GraalVM (Groovy 5 crashes the build outright — oracle/graal#12986 — and Groovy 4's invokedynamic call sites fail at runtime). Don't add the dialect back if you care about native.
- **`NativeRuntimeHints` registers reflection Spring AOT can't see.** Thymeleaf evaluates template expressions via SpEL reflection at runtime, so every type whose methods a template calls must be registered — your records, and JDK types like `Locale` or `List`. When a native page 500s with `MissingReflectionRegistrationError`, add the reported type there.

---

Generated with [ttcli](https://github.com/wimdeblauwe/ttcli), then hardened for DOP. Real-world references for this style: [jitterted/tdd-game](https://github.com/jitterted/tdd-game), [Suigi/event-sourced-tic-tac-toe](https://github.com/Suigi/event-sourced-tic-tac-toe), [zodac/diurnal](https://github.com/zodac/diurnal).
