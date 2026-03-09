# Implementation Plan — M8e → M12

## Current Status (as of 2026-03-08)

| Milestone | Status | Notes |
|-----------|--------|-------|
| **M7** | ✅ COMPLETE | 121 tests, JaCoCo green (65% threshold) |
| **M8a** | ✅ COMPLETE | Exception hierarchy: `BBotException`, `BBotHealthCheckException`, `BBotRestException`, `BBotConfigException`, `BBotGridRowNotFoundException`, `BBotTemplateException` |
| **M8b** | ✅ COMPLETE | `BBotRegistry` + `RestProbe` + `RestResponse` migrated to typed exceptions |
| **M8c** | ✅ COMPLETE | `JsonTemplateEngine`, `TestDataConfig`, `ScenarioState` migrated |
| **M8d** | ✅ COMPLETE | `GridHarness` migrated to `BBotGridRowNotFoundException` |
| **M8e** | ✅ COMPLETE | SLF4J logging — 8 classes instrumented, `slf4j-api` compile dep, last `AssertionError` fixed |
| **M8f** | ✅ COMPLETE | Playwright tracing support — `b-bot.tracing` config block, `PlaywrightManager` wiring |
| **M9** | ✅ COMPLETE | REST client hardening — full verbs, auth, retry, builder, shared HttpClient |
| **M10** | ✅ COMPLETE | Interface extraction — BrowserLifecycle, GridQuery, CellAssertions, RestClient |
| **M11** | ✅ COMPLETE | Instance-based architecture — BBotSession, PicoContainer DI, deprecated statics |
| **M12** | ✅ COMPLETE | CI + docs + Javadoc — `.github/workflows/ci.yml`, Javadoc JAR (0 warnings), MD sync |
| **M13** | ✅ COMPLETE | Enterprise SSO / MFA authentication — see [`SSO_AUTH_PLAN.md`](SSO_AUTH_PLAN.md) |
| **M14** | ✅ COMPLETE | `AppDescriptor` refactor: `@FunctionalInterface` with only `dslFactory()`; `ComponentType` deleted; health/version paths moved to HOCON config; `BBotSession.Builder.initialize()` auto-discovers via `Class.forName()`; zero `.register()` calls in `Hooks.java` |

### Resolved in M8e
All `new AssertionError(...)` replaced with typed `BBotException` across all core classes.

---

## M8e — SLF4J Structured Logging

### Goal
Add SLF4J logging at key decision points across all 8 core classes.
Convention: `LOG.info(...)` for major lifecycle events; `LOG.debug(...)` for per-request/per-scroll detail; `LOG.warn(...)` for fallbacks.

### Changes

#### 1. Parent POM — add `slf4j-api` managed dependency
**File:** `pom.xml`
- Add `org.slf4j:slf4j-api:${slf4j.version}` to `<dependencyManagement>`

#### 2. Core POM — add `slf4j-api` compile dependency
**File:** `b-bot-core/pom.xml`
- Add `org.slf4j:slf4j-api` (compile scope) before existing `slf4j-simple` (test)

#### 3. Fix last `AssertionError` in `NumericComparator`
**File:** `b-bot-core/src/main/java/com/bbot/core/NumericComparator.java`
- Replace `throw new AssertionError(...)` with `throw new BBotException(...)`
- Update `NumericComparatorTest` to assert `BBotException` instead of `AssertionError`

#### 4. Add loggers to 8 classes

| Class | Log Points |
|-------|-----------|
| `BBotConfig` | `INFO` env selected + layers loaded; `DEBUG` overrides applied |
| `BBotRegistry` | `INFO` apps registered + contexts resolved; `DEBUG` health check result, version match |
| `PlaywrightManager` | `INFO` browser type + headless; `DEBUG` viewport, context created/closed |
| `GridHarness` | `DEBUG` which phase succeeded + scroll step count; `WARN` exhaustive search failed |
| `RestProbe` | `DEBUG` request URL + method; `DEBUG` response status |
| `ProbesLoader` | `INFO` bundle loaded (once); `DEBUG` cache hit |
| `TickingCellHelper` | `DEBUG` cell selector + timeout |
| `JsonTemplateEngine` | `DEBUG` template name + bond list being rendered |

### Quality Gate
```bash
# G8e.1 — No raw AssertionError in core main sources
grep -rn "new AssertionError" b-bot-core/src/main/
# Must print: (no output)

# G8e.2 — Loggers present
grep -rn "LoggerFactory.getLogger" b-bot-core/src/main/java/
# Must print: ≥ 8 hits

# G8e.3 — All tests pass
mvn test -pl b-bot-core
```

---

## M8f — Playwright Tracing Support

### Goal
Enable Playwright tracing (screenshots + snapshots) for CI failure diagnosis.
Off by default; activated via `-Db-bot.tracing.enabled=true`.

### Changes

#### 1. Add `b-bot.tracing` to `reference.conf`
```hocon
tracing {
  enabled   = false
  outputDir = "target/playwright-traces"
}
```

#### 2. Wire tracing into `PlaywrightManager`
- `initContext()`: if `b-bot.tracing.enabled=true`, call `ctx.tracing().start(...)`
- `closeContext()`: if tracing active, call `ctx.tracing().stop(...)` with trace file path
- Thread-local boolean to track tracing state

### Quality Gate
```bash
# G8f.1 — Config readable
mvn test -pl b-bot-core  # BBotConfigTest verifies tracing defaults

# G8f.2 — All existing tests still pass
mvn test -pl b-bot-core
```

---

## M9 — REST Client Hardening

### Goal
Full HTTP verb coverage, authentication, retry, shared `HttpClient`, type-safe templates.

### Sub-steps

| Step | Change | Files |
|------|--------|-------|
| 9a | Add PUT, DELETE, PATCH to `RestProbe` | `RestProbe.java`, new tests |
| 9b | Centralise `HttpClient` + `RestProbeBuilder` | `RestProbe.java` refactor |
| 9c | `AuthStrategy` interface + `BearerTokenAuth`, `NoAuth` | New files in `rest/` |
| 9d | `RetryPolicy` record with exponential backoff | New file in `rest/` |
| 9e | PUT/DELETE step definitions in template | `RestApiSteps.java` |
| 9f | JSON template `${bond.QTY!number}` type awareness | `JsonTemplateEngine.java` |

### Quality Gate
```bash
G9.1  RestProbe has GET/POST/PUT/DELETE/PATCH — all unit tested
G9.2  grep "HttpClient.new" b-bot-core/src/main/ → exactly 1 creation site
G9.3  AuthStrategy + RetryPolicy unit tested
G9.4  mvn verify -pl b-bot-sandbox → 66/66
G9.5  mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat → 25/25
```

---

## M10 — Extract Interfaces (Dependency Inversion)

### Goal
Define abstraction interfaces for core components so consumers can mock/substitute.

### Interfaces

| Interface | Default Impl | Key Methods |
|-----------|-------------|-------------|
| `BrowserLifecycle` | `PlaywrightManager` | `initBrowser`, `initContext`, `getPage`, `closeContext`, `closeBrowser` |
| `GridQuery` | `GridHarness` | `findRowByCellValue`, `getSiblingCellText`, `getCellText`, `cellLocator` |
| `CellAssertions` | `TickingCellHelper` | `waitForCellUpdate`, `assertCellValueInRange`, `waitForCellFlash` |
| `RestClient` | `RestProbe` | `get`, `post`, `put`, `delete`, `patch` |

### Quality Gate
```bash
G10.1  ≥ 4 new interfaces in com.bbot.core
G10.2  All default implementations implement their interface
G10.3  Mockability tests (anonymous impls) prove each interface is testable
G10.4  mvn verify -pl b-bot-sandbox → 66/66
```

---

## M11 — Instance-Based Architecture

### Goal
Replace static singletons with `BBotSession`. Deprecate static API.
Backward-compatible: all static methods remain functional as `@Deprecated` delegates.

### Sub-steps Overview

| Step | Change | Status |
|------|--------|--------|
| 11a | Create `BBotSession` value object; `BBotRegistry` statics delegate to it | ✅ |
| 11b | Create `ScenarioContext` (instance-based `ScenarioState`) | ✅ |
| 11c | Wire PicoContainer in sandbox | ✅ |
| 11d | Wire PicoContainer in template | ✅ |
| 11e | `@Deprecated` annotations on static API | ✅ |
| 11f | `GridHarness` / `TickingCellHelper` receive config via constructor | ✅ |

---

### Step 11a — Create `BBotSession`; `BBotRegistry` delegates

**New files:**
- `b-bot-core/src/main/java/com/bbot/core/registry/BBotSession.java`
- `b-bot-core/src/test/java/com/bbot/core/registry/BBotSessionTest.java`

**Modified files:**
- `b-bot-core/src/main/java/com/bbot/core/registry/BBotRegistry.java`

**`BBotSession` design:**
```java
public final class BBotSession {
    private final Map<String, AppDescriptor<?>> descriptors;   // unmodifiable
    private final Map<String, AppContext>        contexts;      // unmodifiable
    private final BBotConfig                     config;

    // Instance methods: dsl(), checkHealth(), assertVersion(), checkAllHealth(), getConfig()
    // All internal helpers (requireDescriptor, requireContext, httpGetStatus, httpGetBody, configMs)

    public static final class Builder {
        // register(String name, AppDescriptor<?>)  — explicit (optional; auto-discovery preferred)
        // initialize(BBotConfig)                   — stores config + auto-discovers descriptor-class entries
        // build()                                  → BBotSession
    }
}
```

**`BBotRegistry` changes:**
- Add `private static volatile BBotSession INSTANCE`
- `register()` → accumulates in a `Builder` (lazily created)
- `initialize(cfg)` → calls `builder.initialize(cfg)`, stores result in `INSTANCE`
- All static methods delegate to `INSTANCE`
- Add `public static BBotSession session()` — returns `INSTANCE`

**Quality gate 11a:**
```bash
mvn test -pl b-bot-core   # all core tests green
# BBotSession fields are all final
# BBotSessionTest: immutability, double-init rejection, dsl/health/version delegation
```

---

### Step 11b — Create `ScenarioContext` (instance-based `ScenarioState`)

**New files:**
- `b-bot-core/src/main/java/com/bbot/core/rest/ScenarioContext.java`
- `b-bot-core/src/test/java/com/bbot/core/rest/ScenarioContextTest.java`

**Modified files:**
- `b-bot-core/src/main/java/com/bbot/core/rest/ScenarioState.java` (internal delegation)

**`ScenarioContext` design:**
```java
public final class ScenarioContext {
    private final Map<String, String> state = new HashMap<>();

    public void put(String key, String value) { ... }
    public Optional<String> get(String key) { ... }
    public String require(String key) { ... }
    public void reset() { ... }
    public String resolve(String template) { ... }
}
```

**`ScenarioState` internal change:**
- `ThreadLocal<Map<String, String>>` → `ThreadLocal<ScenarioContext>`
- All static methods delegate to `ScenarioContext` instance
- Zero external API change

**Quality gate 11b:**
```bash
mvn test -pl b-bot-core   # all core tests green
# ScenarioContextTest mirrors ScenarioStateTest coverage
# ScenarioStateTest passes unchanged (proves delegation)
```

---

### Step 11c — Wire PicoContainer in sandbox

**New files:**
- `b-bot-sandbox/src/test/java/stepdefs/TestWorld.java`

**Modified files:**
- `pom.xml` (parent) — add `cucumber-picocontainer` to `<dependencyManagement>`
- `b-bot-sandbox/pom.xml` — add `cucumber-picocontainer` (test scope)
- All step definition classes → constructor injection of `TestWorld`
- `Hooks.java` → `@Before` uses `ScenarioContext` via `TestWorld`

**`TestWorld` design:**
```java
public class TestWorld {
    private final BBotSession session;
    private final ScenarioContext scenarioContext;

    public TestWorld() {
        this.session = BBotRegistry.session();
        this.scenarioContext = new ScenarioContext();
    }

    public BBotSession session()            { return session; }
    public ScenarioContext scenarioContext() { return scenarioContext; }
    public Page page()                      { return PlaywrightManager.getPage(); }
}
```

**Step definition pattern:**
```java
public class BondBlotterSteps {
    private final TestWorld world;
    private final BlotterDsl dsl;

    public BondBlotterSteps(TestWorld world) {
        this.world = world;
        this.dsl = world.session().dsl("blotter", world.page(), BlotterDsl.class);
    }
}
```

**Quality gate 11c:**
```bash
mvn verify -pl b-bot-sandbox   # 66/66 tests pass (G11.3)
```

---

### Step 11d — Wire PicoContainer in template

**New files:**
- `pt-blotter-regression-template/src/test/java/stepdefs/TestWorld.java`

**Modified files:**
- `pt-blotter-regression-template/pom.xml` — add `cucumber-picocontainer` (test scope)
- All step definition classes → constructor injection of `TestWorld`
- `Hooks.java` → `ScenarioState.reset()` replaced with `scenarioContext.reset()`

**Quality gate 11d:**
```bash
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat   # 25/25 (G11.4)
```

---

### Step 11e — `@Deprecated` annotations on static API

**Modified files:**
- `BBotRegistry.java` — annotate `register()`, `initialize()`, `dsl()`, `checkHealth()`,
  `assertVersion()`, `checkAllHealth()`, `getConfig()`, `reset()`
- `ScenarioState.java` — annotate `put()`, `get()`, `require()`, `reset()`, `resolve()`
- `PlaywrightManager.java` — annotate `initBrowser()`, `initContext()`, `getPage()`,
  `closeContext()`, `closeBrowser()`

**Annotation pattern:**
```java
/** @deprecated Use {@link BBotSession} instance methods instead. */
@Deprecated(since = "1.1", forRemoval = true)
public static void register(AppDescriptor<?> descriptor) { ... }
```

**Quality gate 11e:**
```bash
grep -rn "@Deprecated" b-bot-core/src/main/java/   # ≥ 8 hits (G11.2)
mvn verify -pl b-bot-sandbox       # 66/66
mvn verify -pl pt-blotter-regression-template   # compiles clean
```

---

### Step 11f — `GridHarness` / `TickingCellHelper` receive config via constructor

**Modified files:**
- `GridHarness.java` — add `GridHarness(Page, BBotConfig)` constructor; `cfgMs()`/`cfgInt()`
  become instance methods using injected config (fall back to `BBotRegistry.getConfig()`)
- `TickingCellHelper.java` — add `TickingCellHelper(BBotConfig)` constructor;
  instance method overloads for `waitForCellUpdate`, `assertCellValueInRange`, etc.;
  static methods remain as delegates

**New test files:**
- `b-bot-core/src/test/java/com/bbot/core/GridHarnessConfigTest.java`

**Quality gate 11f:**
```bash
mvn test -pl b-bot-core            # core green
mvn verify -pl b-bot-sandbox       # 66/66 (G11.3)
mvn verify -pl pt-blotter-regression-template   # 24/24 (G11.4)
```

---

### Overall Quality Gates

```bash
G11.1  BBotSession is immutable after build — fields are final, no mutators
G11.2  grep "@Deprecated" b-bot-core/src/main/ → ≥ 8 deprecated methods
G11.3  mvn verify -pl b-bot-sandbox → 66/66
G11.4  mvn verify -pl pt-blotter-regression-template → 25/25
```

---

## M12 — CI Pipeline + Documentation + Javadoc ✅ COMPLETE

### Sub-steps

| Step | Change | Status |
|------|--------|--------|
| 12a | GitHub Actions workflow (unit tests, sandbox, nightly template) | ✅ `.github/workflows/ci.yml` |
| 12b | `maven-javadoc-plugin` in b-bot-core; zero-warning Javadoc | ✅ 0 warnings confirmed |
| 12c | Update README.md, MODULARISATION_DESIGN.md, CLAUDE.md | ✅ |

### Quality Gate (all passed)
```bash
G12.1  GitHub Actions CI — 3-job pipeline (core, sandbox, nightly template)
G12.2  mvn javadoc:javadoc -pl b-bot-core → 0 warnings (failOnWarnings=true)
G12.3  All MD files updated (IMPLEMENTATION_PLAN.md, MODULARISATION_DESIGN.md, CLAUDE.md, README.md)
```

### Implementation Notes

#### 12a — GitHub Actions (`.github/workflows/ci.yml`)
- **`core-tests`**: runs `mvn verify -pl b-bot-core` — unit tests + JaCoCo + Javadoc JAR
- **`sandbox-tests`**: runs `mvn verify -pl b-bot-sandbox` (needs core, installs Playwright browsers)
- **`template-nightly`**: starts mock UAT servers via `scripts/start-mock-uat.sh`, then runs
  `mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat`; only on schedule/dispatch

#### 12b — Javadoc Plugin
- `maven-javadoc-plugin 3.10.1` added to parent `pluginManagement` and `b-bot-core` build
- `failOnWarnings=true` enforced; `-Xdoclint:all -Xdoclint:-missing` (reference / HTML checked)
- Javadoc JAR attached at `verify` phase alongside the regular JAR

#### Housekeeping (done alongside M12)
- `ScenarioState.reset()` added to `Hooks.@Before` in both sandbox and template
  (ensures RestProbe path resolution starts clean each scenario)

---

## Execution Order & Rationale

```
M8e → M8f → M9 → M10 → M11 → M12
```

1. **M8e first** — logging enables debugging of all subsequent refactoring
2. **M8f next** — tracing support pairs naturally with logging changes
3. **M9** — complete REST API before extracting its interface
4. **M10** — interfaces make the instance-based refactor (M11) cleaner
5. **M11 optional** — static API works fine for single-suite JVMs
6. **M12 last** — documentation reflects the final state

**Invariant:** 66/66 sandbox + 25/25 template tests pass after every sub-step.

