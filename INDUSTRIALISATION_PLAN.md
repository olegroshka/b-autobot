# Industrialisation Plan — b-autobot → Production-Grade Library

## Context

b-autobot is a mature 66+24 scenario BDD regression framework with a well-designed
multi-module architecture (b-bot-core / b-bot-sandbox / pt-blotter-regression-template).
This plan takes the codebase from "works well" to "industrial-grade library quality"
through six incremental milestones, each with quality gates.

**Invariant:** 66/66 sandbox + 24/24 template tests must pass at the end of every sub-step.

**Milestones M1–M6 (module split, config, registry, DSL decoupling, consumer template)
are already COMPLETE.  This plan begins at M7.**

---

## Assessment — Five Improvement Areas

### 1. Unit Test Coverage Gap (M7)
b-bot-core has only ~20 unit tests covering `BBotConfig`, `BBotRegistry`, and `AppContext`.
**Zero tests** exist for: `NumericComparator`, `ScenarioState`, `RestResponse`,
`JsonTemplateEngine`, `TestDataConfig`, `TickingCellHelper` pure functions, `ProbesLoader`,
or `ApiAction` lookups. The 66-scenario Cucumber suite is an excellent integration gate
but cannot catch edge-case bugs in utility classes.

### 2. No Structured Error Handling or Logging (M8)
20+ `throw new AssertionError(...)` with formatted strings — no typed exceptions, no
SLF4J logging anywhere in core. Failures are opaque; debugging requires reading stack
traces. No Playwright tracing support for CI failure diagnosis.

### 3. Incomplete REST Client (M9)
`RestProbe` only supports GET and POST. No PUT/DELETE/PATCH, no authentication headers,
no retry, no shared `HttpClient`. Four separate `HttpClient` instances across the codebase.
`JsonTemplateEngine` uses naive string substitution that doesn't handle numeric types.

### 4. No Abstraction Interfaces (M10)
`GridHarness`, `TickingCellHelper`, `PlaywrightManager`, `RestProbe` are concrete `final`
classes. Consumers cannot mock, substitute, or extend them. Violates Dependency Inversion.

### 5. Global Mutable Static State (M11)
`BBotRegistry`, `PlaywrightManager`, `ScenarioState` use static mutable state / ThreadLocal.
Prevents parallel suite execution and makes unit testing fragile. Every core utility
reaches into `BBotRegistry.getConfig()` — hidden global coupling.

---

## M7 — Core Unit Test Coverage ✅ DONE

**Goal:** Raise b-bot-core from ~20 tests to ~80+ tests. Cover every public API in
the untested classes. Add JaCoCo enforcement. This is the safety net for all
subsequent refactoring.

**Result:** 121 tests, 0 failures. JaCoCo coverage check passing (65% threshold,
`PlaywrightManager` and `GridHarness` excluded — browser-dependent). 66/66 sandbox green.

### Sub-step 7a — `NumericComparator` tests (~15 tests)

Create `b-bot-core/src/test/java/com/bbot/core/NumericComparatorTest.java`:

| Test | What it proves |
|------|---------------|
| `equivalent_trailingZeros` | `"100.00"` vs `"100"` → true |
| `equivalent_thousandSeparators` | `"5,937,500.0"` vs `"5937500"` → true |
| `equivalent_currencyPrefix` | `"$98.75"` vs `"98.75"` → true |
| `equivalent_percentageSuffix` | `"4.52%"` vs `"4.52"` → true |
| `equivalent_negativeNumbers` | `"-1.5"` vs `"-1.50"` → true |
| `equivalent_nonNumericFallback` | `"SUBMITTED"` vs `"SUBMITTED"` → true |
| `equivalent_nonNumericMismatch` | `"SUBMITTED"` vs `"PENDING"` → false |
| `equivalent_numericMismatch` | `"98.75"` vs `"99.00"` → false |
| `equivalent_bothEmpty` | `""` vs `""` → true |
| `equivalent_nullInputs` | null vs null → true |
| `assertEquivalent_passesOnMatch` | no exception thrown |
| `assertEquivalent_throwsOnMismatch` | AssertionError with descriptive message |
| `extractFieldValue_simplePath` | `"portfolio_id"` → correct value |
| `extractFieldValue_nestedPath` | `"trades[0].price"` → correct value |
| `extractFieldValue_missingPath` | returns `""` |
| `normalise_stripsAllFormatting` | `"$5,937,500.00"` → `"5937500.00"` |

**Quality gate:**
```bash
mvn test -pl b-bot-core   # all new + existing tests pass
```

---

### Sub-step 7b — `ScenarioState` tests (~8 tests)

Create `b-bot-core/src/test/java/com/bbot/core/rest/ScenarioStateTest.java`:

| Test | What it proves |
|------|---------------|
| `putAndGet_roundTrip` | stored value is retrievable |
| `get_absentKey_returnsEmpty` | no exception for missing key |
| `require_absentKey_throwsWithDiagnostic` | lists available keys in error |
| `require_presentKey_returnsValue` | happy path |
| `reset_clearsAllKeys` | state is empty after reset |
| `resolve_substitutesMultipleTokens` | `"${a} and ${b}"` → correct |
| `resolve_leavesUnresolvedTokensIntact` | `"${missing}"` → `"${missing}"` |
| `threadIsolation` | two threads see independent state |

**Quality gate:**
```bash
mvn test -pl b-bot-core
```

---

### Sub-step 7c — `RestResponse` tests (~10 tests)

Create `b-bot-core/src/test/java/com/bbot/core/rest/RestResponseTest.java`:

| Test | What it proves |
|------|---------------|
| `assertStatus_passes` | correct status → no exception |
| `assertStatus_failsWithBody` | wrong status → AssertionError includes body |
| `assertField_shortForm` | `"status"` → normalised to `"$.status"` |
| `assertField_fullPath` | `"$.items[0].isin"` → works |
| `assertField_mismatch_showsActualAndBody` | descriptive error |
| `assertFieldNotEmpty_nullField_throws` | AssertionError |
| `assertFieldNotEmpty_blankField_throws` | AssertionError |
| `getField_emptyBody_throws` | descriptive error |
| `getField_pathNotFound_throws` | includes path in error |
| `capture_autoAlias` | `"$.inquiry_id"` → stores as `"inquiry_id"` |
| `capture_explicitAlias` | custom alias works |

**Quality gate:**
```bash
mvn test -pl b-bot-core
```

---

### Sub-step 7d — `JsonTemplateEngine` tests (~8 tests)

Create `b-bot-core/src/test/java/com/bbot/core/rest/JsonTemplateEngineTest.java`:

Requires test template files under `b-bot-core/src/test/resources/templates/`.

| Test | What it proves |
|------|---------------|
| `render_bondListTokens` | `${bond.ISIN1}` substituted |
| `render_globalTokens` | `${settlement-date}` substituted |
| `render_scenarioStateTokens` | `${inquiry_id}` substituted from state |
| `render_unresolvedTokensLeftInPlace` | `${missing}` stays literal |
| `render_noBondList` | single-arg render works |
| `renderWithContext_customVars` | caller-supplied vars override globals |
| `render_missingTemplate_throws` | readable AssertionError |
| `render_resolutionOrder` | state > bond > global priority |

**Quality gate:**
```bash
mvn test -pl b-bot-core
```

---

### Sub-step 7e — `TestDataConfig` tests (~10 tests)

Create `b-bot-core/src/test/java/com/bbot/core/data/TestDataConfigTest.java`:

Requires test HOCON data under `b-bot-core/src/test/resources/application.conf` additions.

| Test | What it proves |
|------|---------------|
| `getGlobal_present` | returns Optional.of(value) |
| `getGlobal_absent` | returns Optional.empty() |
| `getAllGlobals_excludesReservedBlocks` | bond-lists, templates, portfolios, service-versions, users excluded |
| `getBondList_present` | returns unmodifiable map |
| `getBondList_absent_throws` | readable AssertionError |
| `resolveBondRef_present` | returns correct ISIN |
| `resolveBondRef_missingField_throws` | lists available fields |
| `getTemplatePath_present` | returns classpath path |
| `getTemplatePath_absent_throws` | readable error |
| `getServiceVersion_present` | returns version string |
| `getUser_present` | returns username |
| `getPortfolio_parsesBondsInOrder` | bonds ordered by key |
| `getPortfolio_fallbackSettlementDate` | uses global when portfolio omits it |

**Quality gate:**
```bash
mvn test -pl b-bot-core
```

---

### Sub-step 7f — `TickingCellHelper` pure functions + `ProbesLoader` (~5 tests)

Create `b-bot-core/src/test/java/com/bbot/core/TickingCellHelperTest.java`:

| Test | What it proves |
|------|---------------|
| `buildCellSelector_format` | produces `.ag-center-cols-container [row-index='N'] [col-id='X']` |
| `parseNumeric_plain` | `"98.75"` → 98.75 |
| `parseNumeric_commasAndCurrency` | `"$5,937,500.00"` → 5937500.00 |
| `parseNumeric_blank_throws` | IllegalArgumentException |

Create `b-bot-core/src/test/java/com/bbot/core/ProbesLoaderTest.java`:

| Test | What it proves |
|------|---------------|
| `load_returnsNonEmptyContent` | bundle.js found on classpath |
| `load_containsAgGridProbes` | content contains `agGridProbes` |
| `load_cachedOnSecondCall` | same String reference returned (identity check) |

**Quality gate:**
```bash
mvn test -pl b-bot-core
```

---

### Sub-step 7g — `BBotConfig.getApiAction` tests (~5 tests)

Add to existing `BBotConfigTest.java`:

| Test | What it proves |
|------|---------------|
| `getApiAction_findsAcrossApps` | action found under correct app |
| `getApiAction_unknownName_throws` | readable error with action name |
| `getApiAction_recordFields` | name, method, app, path, template correct |
| `getApiAction_nullableTemplate` | GET action has null template |
| `getApiAction_noAppsBlock_throws` | readable error |

Requires test HOCON additions with sample api-actions.

**Quality gate:**
```bash
mvn test -pl b-bot-core
```

---

### Sub-step 7h — JaCoCo enforcement

Add `jacoco-maven-plugin` to `b-bot-core/pom.xml`:
- `prepare-agent` goal in `initialize` phase
- `report` goal in `test` phase
- `check` goal in `verify` phase with rule: 80% line coverage on `com.bbot.core.*`

**Quality gate:**
```bash
mvn verify -pl b-bot-core              # JaCoCo ≥ 80% line coverage
mvn verify -pl b-bot-sandbox           # 66/66
```

---

### Milestone-Level Quality Gates

```bash
# G7.1 — Core unit test count
mvn test -pl b-bot-core
# Must print: Tests run: 80+, Failures: 0, Errors: 0

# G7.2 — JaCoCo coverage threshold
mvn verify -pl b-bot-core
# Must print: BUILD SUCCESS with jacoco-check passing

# G7.3 — Sandbox regression
mvn verify -pl b-bot-sandbox
# Must print: Tests run: 66, Failures: 0, Errors: 0

# G7.4 — Template regression (mock UAT)
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat
# Must print: Tests run: 24, Failures: 0, Errors: 0
```

**Commit message:** `M7: core unit test coverage — 80+ tests, JaCoCo ≥ 80%`

---

## M8 — Exception Hierarchy + Structured Logging

**Goal:** Replace 20+ raw `new AssertionError(...)` with a typed exception hierarchy.
Add SLF4J logging at key decision points. Add Playwright tracing support.

### Sub-step 8a — Exception hierarchy

Create `b-bot-core/src/main/java/com/bbot/core/exception/`:

```
BBotException extends RuntimeException              — base
├── BBotConfigException                              — config loading / missing keys
├── BBotHealthCheckException (url, status, body)     — health/version failures
├── BBotGridRowNotFoundException (colId, text, timeout) — row lookup failures
├── BBotRestException (method, url, status, body)    — REST probe failures
└── BBotTemplateException (templateName, cause)      — template loading / token errors
```

Each exception carries **structured fields** (not just message strings) so consumers
can programmatically inspect failures in custom reporters.

### Sub-step 8b — Migrate `BBotRegistry` + `RestProbe` + `RestResponse`

Replace `throw new AssertionError(...)` → typed exceptions:
- `BBotRegistry.checkHealth` / `assertVersion` → `BBotHealthCheckException`
- `RestProbe.get` / `post` → `BBotRestException`
- `RestResponse.assertStatus` / `assertField` / `getField` → `BBotRestException`
- Update unit tests to assert new exception types

### Sub-step 8c — Migrate `JsonTemplateEngine`, `TestDataConfig`, `ScenarioState`

- `JsonTemplateEngine` → `BBotTemplateException`
- `TestDataConfig` → `BBotConfigException`
- `ScenarioState.require()` → `BBotConfigException`

### Sub-step 8d — Migrate `GridHarness`

- `scrollProbe` `throw new RuntimeException(...)` → `BBotGridRowNotFoundException`

### Sub-step 8e — SLF4J logging

Add `private static final Logger LOG = LoggerFactory.getLogger(X.class)` to:
- `BBotConfig` — env selected, layers loaded
- `BBotRegistry` — apps registered, contexts resolved, health check result
- `PlaywrightManager` — browser type, headless, viewport
- `GridHarness` — which phase succeeded, scroll step count
- `RestProbe` — request URL + method, response status
- `ProbesLoader` — bundle loaded, cached
- `TickingCellHelper` — cell selector, timeout used

Level convention: `LOG.debug(...)` for operational detail, `LOG.warn(...)` for fallbacks.
No `LOG.info()` by default — clean output.

### Sub-step 8f — Playwright tracing support

Add to `reference.conf`:
```hocon
b-bot.tracing {
  enabled   = false
  outputDir = "target/playwright-traces"
}
```

In `PlaywrightManager.initContext()`: if enabled, start tracing.
In `PlaywrightManager.closeContext()`: stop and save trace file.

### Quality Gates

```bash
# G8.1 — No raw AssertionError in core main sources
grep -rn "new AssertionError" b-bot-core/src/main/
# Must print: (no output)

# G8.2 — Loggers present
grep -rn "LoggerFactory.getLogger" b-bot-core/src/main/java/
# Must print: ≥ 8 hits

# G8.3 — All tests pass
mvn test -pl b-bot-core                # unit tests
mvn verify -pl b-bot-sandbox           # 66/66
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat  # 24/24
```

**Commit message:** `M8: typed exception hierarchy, SLF4J logging, Playwright tracing`

---

## M9 — REST Client Hardening

**Goal:** Full HTTP verb coverage, authentication, retry, shared `HttpClient`,
JSON template type safety.

### Sub-step 9a — PUT, DELETE, PATCH in `RestProbe`

Add methods + unit tests using JDK `HttpServer` stubs.

### Sub-step 9b — Centralise `HttpClient` + `RestProbeBuilder`

```java
RestProbeBuilder.forApp("blotter")
    .apiBase(ctx.getApiBaseUrl())
    .connectTimeout(Duration.ofSeconds(10))
    .defaultHeaders(Map.of("X-Correlation-Id", "test-run-001"))
    .build();
```

Keep `RestProbe.of(apiBase)` as the simple shorthand.
Eliminate 4 separate `HttpClient` instances.

### Sub-step 9c — Authentication strategy

```java
public interface AuthStrategy {
    void apply(HttpRequest.Builder builder);
}
```

Built-in: `BearerTokenAuth(token)`, `NoAuth` (default).
Token from config: `b-bot.auth.token = ${?B_BOT_UAT_TOKEN}`.

### Sub-step 9d — Retry with exponential backoff

```java
public record RetryPolicy(int maxRetries, long initialDelayMs, Set<Integer> retryableStatuses) {
    public static final RetryPolicy NONE = new RetryPolicy(0, 0, Set.of());
}
```

### Sub-step 9e — PUT/DELETE step definitions

Add to `RestApiSteps.java` in template:
```gherkin
When I PUT template "update-config" to app "config-service" path "/api/config/ns/type/key"
When I DELETE from app "blotter" path "/api/inquiry/${inquiry_id}"
```

### Sub-step 9f — JSON template type awareness

Convention: `${bond.QTY!number}` → unquoted numeric, `${bond.ISIN}` → quoted string.
Unresolved tokens left in place.

### Quality Gates

```bash
G9.1  RestProbe has GET/POST/PUT/DELETE/PATCH — unit tested
G9.2  grep "HttpClient.new" b-bot-core/src/main/ → exactly 1 creation site
G9.3  AuthStrategy + RetryPolicy unit tested
G9.4  mvn verify -pl b-bot-sandbox               → 66/66
G9.5  mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat → 24/24
```

**Commit message:** `M9: REST client hardening — full verbs, auth, retry, type-safe templates`

---

## M10 — Extract Interfaces (Dependency Inversion)

**Goal:** Define abstraction interfaces for core components. Allow consumers to
substitute custom implementations.

### Interfaces

| Interface | Default Implementation | Key Methods |
|-----------|----------------------|-------------|
| `BrowserLifecycle` | `PlaywrightManager` | `initBrowser`, `initContext`, `getPage`, `closeContext`, `closeBrowser` |
| `GridQuery` | `GridHarness` | `findRowByCellValue`, `getSiblingCellText`, `getCellText`, `cellLocator` |
| `CellAssertions` | `TickingCellHelper` | `waitForCellUpdate`, `assertCellValueInRange`, `waitForCellFlash` |
| `RestClient` | `RestProbe` | `get`, `post`, `put`, `delete`, `patch` |

### Sub-steps

- **10a** — `BrowserLifecycle` interface; `PlaywrightManager` implements
- **10b** — `GridQuery` interface; `GridHarness` implements; remove `final`
- **10c** — Make `TickingCellHelper` instance-based; `CellAssertions` interface; static methods become backward-compat shims
- **10d** — `RestClient` interface; `RestProbe` implements
- **10e** — Mockability tests: anonymous implementations prove each interface is testable without Playwright

### Quality Gates

```bash
G10.1  ≥ 4 new interfaces in com.bbot.core
G10.2  All default implementations implement their interface
G10.3  mvn test -pl b-bot-core            → all tests pass
G10.4  mvn verify -pl b-bot-sandbox       → 66/66
G10.5  mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat → 24/24
```

**Commit message:** `M10: interface extraction — BrowserLifecycle, GridQuery, CellAssertions, RestClient`

---

## M11 — Instance-Based Architecture (Eliminate Global State)

**Goal:** Replace static singletons with instance-based `BBotSession`. Wire via
Cucumber PicoContainer. Deprecate (but preserve) static API for backward compatibility.

### `BBotSession`

```java
BBotSession session = BBotSession.builder()
    .config(BBotConfig.load().withOverrides(...))
    .register(new BlotterAppDescriptor())
    .register(new ConfigServiceDescriptor())
    .build();  // immutable — resolves all AppContexts

BlotterDsl dsl = session.dsl("blotter", page, BlotterDsl.class);
session.checkHealth("blotter");
```

### Sub-steps

- **11a** — Create `BBotSession` value object; `BBotRegistry` statics delegate to it
- **11b** — Create `ScenarioContext` (instance-based `ScenarioState`)
- **11c** — Wire PicoContainer in sandbox
- **11d** — Wire PicoContainer in template
- **11e** — `@Deprecated` annotations on static API
- **11f** — `GridHarness` / `TickingCellHelper` receive config via constructor

### Quality Gates

```bash
G11.1  BBotSession is immutable after build
G11.2  ScenarioContext is non-static
G11.3  Sandbox uses PicoContainer injection
G11.4  mvn verify -pl b-bot-sandbox            → 66/66
G11.5  mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat → 24/24
G11.6  grep "@Deprecated" b-bot-core/src/main/ → ≥ 8 deprecated methods
```

**Commit message:** `M11: instance-based architecture — BBotSession, PicoContainer DI, deprecated statics`

---

## M12 — CI Pipeline + Documentation + Javadoc

**Goal:** Reference CI pipeline, published Javadoc, updated design docs.

### Sub-steps

- **12a** — GitHub Actions workflow (unit tests, sandbox regression, nightly template)
- **12b** — `maven-javadoc-plugin` in b-bot-core; zero-warning Javadoc generation
- **12c** — Update README.md, MODULARISATION_DESIGN.md, CLAUDE.md with new patterns

### Quality Gates

```bash
G12.1  GitHub Actions CI green
G12.2  mvn javadoc:javadoc -pl b-bot-core → 0 warnings
G12.3  All MD files updated
G12.4  mvn verify → BUILD SUCCESS (all modules)
```

**Commit message:** `M12: CI pipeline, Javadoc, documentation refresh`

---

## Milestone Summary

| Milestone | Focus | Risk | Tests |
|-----------|-------|------|-------|
| **M7** | Unit test coverage + JaCoCo | Low | 66+24 |
| **M8** | Exception hierarchy + logging + tracing | Low | 66+24 |
| **M9** | REST client hardening | Low–Med | 66+24 |
| **M10** | Interface extraction | Medium | 66+24 |
| **M11** | Instance-based architecture | High | 66+24 |
| **M12** | CI + docs + Javadoc | Low | 66+24 |

## Ordering Rationale

- **M7 first** — unit tests are the safety net for everything that follows
- **M8 before M9–M11** — logging makes debugging refactoring failures possible
- **M9 before M10** — complete the REST API before extracting its interface
- **M10 before M11** — interfaces make the instance-based refactor cleaner
- **M11 is optional** — static API works fine for single-suite JVMs; defer if unnecessary
- **M12 last** — documentation reflects the final state

---

**Current status:** M7 COMPLETE (121 tests, JaCoCo green, 66/66 sandbox). M8 COMPLETE (exception hierarchy, SLF4J logging, Playwright tracing). M9 COMPLETE (full HTTP verbs, auth strategy, retry policy, builder, shared HttpClient). M10 COMPLETE (BrowserLifecycle, GridQuery, CellAssertions, RestClient interfaces). M11 COMPLETE (BBotSession, ScenarioContext, PicoContainer DI, 18 @Deprecated annotations, GridHarness/TickingCellHelper config injection). Next: M12 sub-step 12a.

