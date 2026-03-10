# Plan: Test Data & Config Normalisation — Eliminate Duplication, Domain-Driven Structure

Three interrelated design issues are addressed together because they share the same root cause: domain knowledge is scattered across config and Java code instead of being defined once in the right place.

| # | Issue | Root Cause |
|---|---|---|
| A | Bond/ISIN data duplicated across `bond-lists` and `portfolios` | No normalised bond catalogue |
| B | API paths like `/api/deployments` duplicated in `health-check-path`, DSL classes, and `api-actions` | Paths hardcoded instead of referencing the action registry |
| C | `TestDataConfig` is a monolithic God-class with no interface, no abstraction, and domain-specific logic that should be per-app | Missing `TestDataParser` abstraction on `AppDescriptor` |

---

## A. Bond Data Normalisation

### Problem

The `b-bot.test-data` HOCON config has significant structural duplication and inconsistency. The same bond identifiers and reference data are defined in multiple disconnected sections that must be kept in sync manually.

#### Duplication Map — `application-mockuat.conf`

| ISIN | Times defined | Locations |
|---|---|---|
| `US912828YJ02` | 2× | `bond-lists.HYPT_1.ISIN1` + `portfolios.HYPT_1.bonds.line-1.isin` |
| `XS2346573523` | 2× | `bond-lists.HYPT_1.ISIN2` + `portfolios.HYPT_1.bonds.line-2.isin` |
| `DE000A0TGJ55` | **4×** | `bond-lists.CANCEL_DEALER_1.ISIN1` + `bond-lists.CANCEL_CUSTOMER_1.ISIN1` + `portfolios.CANCEL_DEALER_1.bonds.line-1.isin` + `portfolios.CANCEL_CUSTOMER_1.bonds.line-1.isin` |
| `IT0005240830` | **4×** | Same pattern as above across CANCEL_DEALER_1 / CANCEL_CUSTOMER_1 |
| `XS2530201644` | 2× | `bond-lists.CANCEL_BLOTTER_1.ISIN1` + `portfolios.CANCEL_BLOTTER_1.bonds.line-1.isin` |
| `DE0001102580` | 2× | `bond-lists.CANCEL_BLOTTER_1.ISIN2` + `portfolios.CANCEL_BLOTTER_1.bonds.line-2.isin` |

**Total: 16 ISIN declarations where 9 unique bonds exist.**

Additionally:
- `portfolios.CANCEL_DEALER_1.bonds` and `portfolios.CANCEL_CUSTOMER_1.bonds` are **byte-for-byte identical** (full copy-paste of both bond lines including description, maturity, coupon, client).
- Bond reference data (description, maturity, coupon) that belongs to the **instrument** is repeated inside **trade-level** portfolio lines.
- `bond-lists` uses a flat positional schema (`ISIN1`, `DESC1`, `COUPON1`) while `portfolios.bonds` uses structured objects — two incompatible schemas for the same domain entity.

#### Structural Inconsistencies

1. **`bond-lists` vs `portfolios`** — Two disconnected representations of the same bond universe. `bond-lists` is a flat key→value map (`ISIN1`, `ISIN2`); `portfolios.bonds` is a structured list of objects. Neither references the other.
2. **Positional naming** — `ISIN1`/`DESC1`/`COUPON1` in bond-lists is fragile. Adding a bond in the middle shifts all indices. No semantic identity.
3. **`users` duplication** — `b-bot.apps.blotter.users` and `b-bot.test-data.users` define the same role→username mappings independently.
4. **`settlement-date`** — Global scalar duplicated inside each portfolio's `settlement-date` field (the fallback mechanism exists in `TestDataConfig.getPortfolio()` but most portfolios still declare it explicitly).

### Solution — Normalised Bond Catalogue

Define each bond instrument **once** in a `bonds` catalogue. Portfolios reference bonds by catalogue ID and add only trade-level parameters. The `bond-lists` section is eliminated entirely.

#### Config Structure

```hocon
b-bot.test-data {

  settlement-date = "2026-03-21"

  # ── Bond catalogue — each instrument defined ONCE ──────────────────────────
  bonds {
    UST-2Y       { isin = "US912828YJ02", description = "UST 4.25% 2034",           maturity = "2034-11-15", coupon = 4.250 }
    EUR-HY-XS23  { isin = "XS2346573523", description = "EUR IG Corp 3.5% 2029",    maturity = "2029-03-20", coupon = 3.500 }
    US-HY-GS     { isin = "US38141GXZ20", description = "Goldman Sachs 5.15% 2026", maturity = "2026-05-22", coupon = 5.150 }
    UK-GILT-27   { isin = "GB0031348658", description = "UK Gilt 1.25% 2027",       maturity = "2027-07-22", coupon = 1.250 }
    FR-OAT-28    { isin = "FR0014004L86", description = "OAT 0.75% 2028",           maturity = "2028-05-25", coupon = 0.750 }
    DE-BUND-32   { isin = "DE000A0TGJ55", description = "German Bund 1.75% 2032",   maturity = "2032-07-04", coupon = 1.750 }
    IT-BTP-32    { isin = "IT0005240830", description = "Italian BTP 2.15% 2032",    maturity = "2032-09-01", coupon = 2.150 }
    EUR-HY-XS25  { isin = "XS2530201644", description = "EUR HY Bond 6.5% 2028",   maturity = "2028-04-14", coupon = 6.500 }
    DE-BUND-30   { isin = "DE0001102580", description = "German Bund 0.25% 2030",   maturity = "2030-02-15", coupon = 0.250 }
  }

  # ── Portfolios — trade-level params only; bonds by catalogue reference ─────
  portfolios {
    HYPT_1 {
      pt-id = "PT_TEST_20260321_AA01"
      lines {
        line-1 { bond = UST-2Y,      quantity = 2000000, side = "Buy",  client = "BLACKROCK", currency = "USD" }
        line-2 { bond = EUR-HY-XS23, quantity = 1500000, side = "Sell", client = "PIMCO",     currency = "EUR" }
      }
    }
    IGPT_1 {
      pt-id = "PT_TEST_20260321_BB01"
      lines {
        line-1 { bond = UK-GILT-27, quantity = 1000000, side = "Buy", client = "FIDELITY", currency = "GBP" }
        line-2 { bond = FR-OAT-28,  quantity = 1000000, side = "Buy", client = "AMUNDI",   currency = "EUR" }
      }
    }
    # Shared cancel lines — defined once, referenced by HOCON substitution
    _cancel-lines-DE-IT {
      line-1 { bond = DE-BUND-32, quantity = 1000000, side = "Buy", client = "BARCLAYS",         currency = "EUR" }
      line-2 { bond = IT-BTP-32,  quantity = 1000000, side = "Buy", client = "SOCIETE GENERALE", currency = "EUR" }
    }
    CANCEL_DEALER_1 {
      pt-id = "PT_CANCEL_D_20260321"
      lines = ${b-bot.test-data.portfolios._cancel-lines-DE-IT}
    }
    CANCEL_CUSTOMER_1 {
      pt-id = "PT_CANCEL_C_20260321"
      lines = ${b-bot.test-data.portfolios._cancel-lines-DE-IT}
    }
    CANCEL_BLOTTER_1 {
      pt-id = "PT_CANCEL_B_20260321"
      lines {
        line-1 { bond = EUR-HY-XS25, quantity = 1000000, side = "Buy", client = "BNP PARIBAS",  currency = "EUR" }
        line-2 { bond = DE-BUND-30,  quantity = 1000000, side = "Buy", client = "DEUTSCHE BANK", currency = "EUR" }
      }
    }
  }
}
```

#### Java Changes

**New `Bond` record** (`b-bot-core/.../data/Bond.java`):
```java
public record Bond(String id, String isin, String description, String maturity, double coupon) {}
```

**Updated `PortfolioBond`** — carries a `Bond` reference, delegates reference-data fields:
```java
public record PortfolioBond(Bond bond, long quantity, String side, String currency, long notional, String client) {
    public String isin()        { return bond.isin(); }
    public String description() { return bond.description(); }
    public String maturity()    { return bond.maturity(); }
    public double coupon()      { return bond.coupon(); }
}
```

**Updated `TestDataConfig`**:
- New `getBond(String bondId)` returning `Bond` from the catalogue.
- `getPortfolio()` resolves `bond = UST-2Y` references automatically from the catalogue.
- Remove `getBondList()` and `resolveBondRef()` (replaced by bond-catalogue and portfolio-line lookups).
- New `resolvePortfolioBondIsin(String portfolio, String lineKey)` for feature-file steps.
- Add `"bonds"` to the `getAllGlobals()` exclusion filter.

#### Feature File Vocabulary

**Before** (positional, non-semantic):
```gherkin
Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "PENDING"
```

**After** — reference by bond catalogue ID directly:
```gherkin
Then the row with bond "UST-2Y" should have status "PENDING"
```

Backward-compat bridge steps can keep the old syntax working during migration by translating `ISIN1` → `line-1` → portfolio lookup.

#### Duplication Scorecard

| Concern | Before | After |
|---|---|---|
| ISIN `US912828YJ02` | 2× (bond-list + portfolio) | **1×** (bond catalogue) |
| ISIN `DE000A0TGJ55` | 4× (2 bond-lists + 2 portfolios) | **1×** (bond catalogue) |
| Cancel bond lines copy-paste | 2× identical blocks | **1×** (HOCON `${}`) |
| Bond reference data (desc/mat/coupon) | Repeated per-portfolio line | **1×** (bond catalogue) |
| `bond-lists` section | Exists (redundant with portfolios) | **Removed** |

---

## B. API Action Path Deduplication

### Problem

API paths like `/api/deployments`, `/api/inquiries`, `/api/config` are hardcoded in **multiple places** that must stay in sync:

| Path | Locations |
|---|---|
| `/api/deployments` | `health-check-path` in conf × 2 (sandbox + regression), `DeploymentDsl.java` × 7 hardcoded occurrences, `MockDeploymentServer.java` × 4 |
| `/api/inquiries` | `health-check-path` in conf, `api-actions.list-inquiries.path` in conf (same path, declared twice) |
| `/api/config` | `health-check-path` in conf × 2, `ConfigServiceDsl.java` hardcoded |

The `api-actions` system already exists as the right place for path definitions, but `health-check-path` and DSL classes don't reference it — they duplicate the paths independently.

### Solution — Actions as Single Source of API Paths

#### 1. `health-check-path` should reference an action name, not a raw path

**Before:**
```hocon
blotter {
  health-check-path = "/api/inquiries"
  api-actions {
    list-inquiries { method = "GET", path = "/api/inquiries" }   # same path, defined twice!
  }
}
deployment {
  health-check-path = "/api/deployments"    # no action defined, path only here
}
```

**After — every app defines its API surface as actions; health check references an action:**
```hocon
blotter {
  health-check-action = "list-inquiries"    # references the action, not a raw path
  api-actions {
    list-inquiries { method = "GET", path = "/api/inquiries" }
  }
}
deployment {
  api-actions {
    list-deployments    { method = "GET", path = "/api/deployments" }
    get-deployment      { method = "GET", path = "/api/deployments/{name}" }
  }
  health-check-action = "list-deployments"  # references the action
}
config-service {
  api-actions {
    list-config { method = "GET", path = "/api/config" }
  }
  health-check-action = "list-config"
}
```

**Key insight:** Every HTTP path the framework touches should be an `api-action`. The action registry becomes the **living API contract** — a single place documenting every endpoint the test suite interacts with.

#### 2. DSL classes should receive actions from `AppContext`, not hardcode paths

**Before** (`DeploymentDsl.java`):
```java
URI.create(apiBase + "/api/deployments")  // hardcoded 7 times
```

**After** — DSL receives its `AppContext` and resolves paths from the action registry:
```java
public final class DeploymentDsl {
    private final AppContext ctx;

    public DeploymentDsl(AppContext ctx) { this.ctx = ctx; }

    private String actionUrl(String actionName) {
        return ctx.getApiBaseUrl() + ctx.getActionPath(actionName);
    }

    private List<Map<String, Object>> fetchAll() {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(actionUrl("list-deployments")))   // resolved from config
            .GET().build();
        // ...
    }
}
```

#### 3. `AppContext` gains action-awareness

```java
public final class AppContext {
    // ...existing fields...

    /** Returns the path for a named api-action declared under this app. */
    public String getActionPath(String actionName) {
        return config.getAppActionPath(name, actionName);
    }
}
```

#### Impact

- `/api/deployments` defined **once** (in `api-actions.list-deployments.path`) instead of 7+ times.
- Adding/changing an endpoint path → edit one line of config.
- DSL classes become path-agnostic — they reference action names.
- `health-check-path` config key replaced by `health-check-action` — no raw path duplication.

---

## C. `TestDataConfig` Abstraction — Per-App Domain Parser via `AppDescriptor`

### Problem

`TestDataConfig` is a monolithic concrete class with:
- **No interface** — no abstraction boundary, untestable in isolation.
- **No extensibility** — every domain concept (bonds, portfolios, service-versions) is baked into one class.
- **Domain-specific logic** — it knows about bonds, ISINs, portfolios, coupon rates — concepts specific to the blotter app. A different app (e.g. an FX blotter) would have completely different test-data structures (currency pairs, tenors, fixing dates).
- **Implicit coupling** — every app in the project shares one `TestDataConfig` even though each app has different domain data needs.

There is no way for an `AppDescriptor` to declare "here's how to parse my app's test data" — the framework assumes every app speaks the same domain language.

### Solution — `TestDataParser` Interface on `AppDescriptor`

#### New `TestDataParser<T>` interface in core

```java
/**
 * Parses domain-specific test data from the {@code b-bot.test-data} config block.
 * Each app descriptor can return its own parser type — bond blotter data, FX data, etc.
 *
 * @param <T> the domain-specific parsed result type (e.g. {@code BlotterTestData})
 */
public interface TestDataParser<T> {

    /** Parse the raw HOCON config into a typed domain object. */
    T parse(Config testDataConfig);
}
```

#### `AppDescriptor` gains a `testDataParser()` hook

```java
public interface AppDescriptor<D> {
    DslFactory<D> dslFactory();

    /** Returns a parser for this app's domain-specific test data, or null if the app has no test data. */
    default TestDataParser<?> testDataParser() { return null; }
}
```

#### Domain-specific parsed type per app

```java
/** Typed test-data for the blotter app — bonds, portfolios, etc. */
public record BlotterTestData(
    Map<String, Bond> bonds,
    Map<String, Portfolio> portfolios,
    Map<String, String> serviceVersions,
    Map<String, String> users,
    Map<String, String> globals
) {}
```

```java
/** Parses blotter-specific test data from the b-bot.test-data block. */
public final class BlotterTestDataParser implements TestDataParser<BlotterTestData> {
    @Override
    public BlotterTestData parse(Config testDataConfig) {
        // Parse bonds catalogue, portfolios (resolving bond refs), etc.
        // All the logic currently in TestDataConfig moves here.
    }
}
```

#### `BlotterDescriptor` wires its parser

```java
public final class BlotterDescriptor implements AppDescriptor<PtBlotterDsl> {
    @Override public DslFactory<PtBlotterDsl> dslFactory() {
        return (ctx, page) -> new PtBlotterDsl(page, ctx);
    }

    @Override public TestDataParser<?> testDataParser() {
        return new BlotterTestDataParser();
    }
}
```

#### `AppContext` / `BBotSession` exposes the parsed test data

```java
public final class AppContext {
    private final Object parsedTestData;  // parsed by the descriptor's TestDataParser

    @SuppressWarnings("unchecked")
    public <T> T getTestData(Class<T> type) {
        return type.cast(parsedTestData);
    }
}
```

Usage in steps:
```java
BlotterTestData td = session.context("blotter").getTestData(BlotterTestData.class);
Bond bond = td.bonds().get("UST-2Y");
```

#### What happens to `TestDataConfig`?

It becomes either:
1. **A thin utility base class** with generic helpers (global variable resolution, template registry) that domain parsers can delegate to, OR
2. **Deprecated and removed** — its methods migrate into `BlotterTestDataParser`.

The template registry (`getTemplatePath()`) and service-versions (`getServiceVersion()`) are generic enough to stay in core as utility methods. Bond/portfolio parsing moves to the blotter-specific parser.

#### Impact

- **SRP:** Each parser knows about its own domain. Blotter parser knows bonds. FX parser could know currency pairs.
- **OCP:** Adding a new app with new domain data = new parser class. No changes to core.
- **ISP:** Apps that don't have test data return `null` — no forced dependency.
- **Testability:** Parsers can be unit-tested with synthetic HOCON snippets.

---

## Files Affected (All Three Refactorings)

### Config files
- `b-bot-core/src/main/resources/reference.conf` — skeleton + comments
- `b-bot-core/src/test/resources/application.conf` — core unit test data
- `b-bot-sandbox/src/test/resources/application.conf`
- `pt-blotter-regression-template/src/test/resources/application-mockuat.conf`

### Java source (core)
- `b-bot-core/.../data/TestDataConfig.java` — refactor into thin utility + deprecate domain methods
- `b-bot-core/.../data/Portfolio.java` — update to use `Bond` reference
- `b-bot-core/.../data/PortfolioBond.java` — update to carry `Bond`
- **New:** `b-bot-core/.../data/Bond.java` — bond catalogue record
- **New:** `b-bot-core/.../data/TestDataParser.java` — interface
- `b-bot-core/.../registry/AppDescriptor.java` — add `testDataParser()` default method
- `b-bot-core/.../registry/AppContext.java` — add `getTestData()`, `getActionPath()`
- `b-bot-core/.../registry/BBotSession.java` — invoke parsers during `build()`
- `b-bot-core/.../config/BBotConfig.java` — add `getAppActionPath()`, update `health-check-action` resolution

### Java source (consumers)
- `descriptors/BlotterDescriptor.java` — return `BlotterTestDataParser`
- **New:** `BlotterTestDataParser.java` — blotter-specific parsing (bonds, portfolios)
- **New:** `BlotterTestData.java` — typed record for blotter test data
- DSL classes (`DeploymentDsl`, `ConfigServiceDsl`) — replace hardcoded paths with `ctx.getActionPath()`
- Step definitions — update `testData()` calls to use typed `getTestData(BlotterTestData.class)`
- Feature files — migrate `bond "HYPT_1" field "ISIN1"` to `bond "UST-2Y"`

### Feature files
- All `.feature` files using `from "HYPT_1" field "ISIN1"` pattern

---

## Migration Strategy

### Phase 1 — Bond Catalogue + API Action Paths (Config-layer, no breaking changes)
1. Add `bonds` catalogue to all config files.
2. Add `api-actions` blocks to `deployment` and `config-service` apps.
3. Change `health-check-path` to `health-check-action` (referencing action name).
4. Keep `bond-lists` temporarily for backward compat.
5. Update `BBotConfig` to resolve health-check from action paths.
6. Add `Bond` record and `getBond()` to `TestDataConfig`.

### Phase 2 — TestDataParser Abstraction (Core interface, no breaking changes)
1. Add `TestDataParser<T>` interface to core.
2. Add `testDataParser()` default method to `AppDescriptor`.
3. Add `getTestData()` to `AppContext`.
4. Wire parsing into `BBotSession.build()`.
5. Create `BlotterTestDataParser` + `BlotterTestData` — initially delegates to existing `TestDataConfig` methods.

### Phase 3 — DSL Path Deduplication + Portfolio Migration
1. Add `getActionPath()` to `AppContext`.
2. Refactor `DeploymentDsl`, `ConfigServiceDsl` to use `ctx.getActionPath()` instead of hardcoded paths.
3. Update `getPortfolio()` to resolve `bond = UST-2Y` references from catalogue.
4. Update `PortfolioBond` to carry a `Bond` reference.

### Phase 4 — Feature File Migration + Cleanup
1. Add new step definitions (`bond "UST-2Y"` syntax).
2. Migrate feature files from `from "HYPT_1" field "ISIN1"` to `bond "UST-2Y"`.
3. Remove deprecated `resolveBondRef()`, `getBondList()`.
4. Remove `bond-lists` from all configs.
5. Deprecate domain-specific methods on `TestDataConfig` (move to `BlotterTestDataParser`).

---

## Detailed Implementation Plan

### Milestone 1 — Core Abstractions (no breaking changes, no config migration)

> **Goal:** Introduce all new types and interfaces in `b-bot-core`. Existing code continues to compile and all existing tests pass unchanged.

#### Step 1.1 — `Bond` record

**File:** `b-bot-core/src/main/java/com/bbot/core/data/Bond.java` (new)

```java
public record Bond(String id, String isin, String description, String maturity, double coupon) {}
```

**Quality gate:**
- `mvn compile -pl b-bot-core` — compiles.
- No existing test changes.

#### Step 1.2 — `TestDataParser<T>` interface

**File:** `b-bot-core/src/main/java/com/bbot/core/data/TestDataParser.java` (new)

```java
@FunctionalInterface
public interface TestDataParser<T> {
    T parse(Config rootConfig);
}
```

**Quality gate:**
- `mvn compile -pl b-bot-core` — compiles.

#### Step 1.3 — `AppDescriptor.testDataParser()` default method

**File:** `b-bot-core/src/main/java/com/bbot/core/registry/AppDescriptor.java`

Add:
```java
default TestDataParser<?> testDataParser() { return null; }
```

**Quality gate:**
- `mvn test -pl b-bot-core` — all 19 existing tests pass (default returns `null`, no consumer breaks).
- Verify `AppDescriptor` remains `@FunctionalInterface` — adding a default method preserves this.

#### Step 1.4 — `AppContext` gains `getActionPath()` and `getTestData()`

**File:** `b-bot-core/src/main/java/com/bbot/core/registry/AppContext.java`

Add two methods:
```java
public String getActionPath(String actionName) {
    return config.getAppActionPath(name, actionName);
}

public <T> T getTestData(Class<T> type) {
    return type.cast(parsedTestData);
}
```

Add `parsedTestData` field, set from `BBotSession.Builder.build()`.

**File:** `b-bot-core/src/main/java/com/bbot/core/config/BBotConfig.java`

Add `getAppActionPath(String appName, String actionName)` — returns the `path` field from `b-bot.apps.{appName}.api-actions.{actionName}`.

**Quality gate:**
- `mvn test -pl b-bot-core` — all existing tests pass.
- New unit test: `AppContextTest.getActionPath_resolvesFromConfig`.
- New unit test: `BBotConfigTest.getAppActionPath_present` / `_absent_throws`.

#### Step 1.5 — `BBotSession.Builder.build()` invokes parsers

**File:** `b-bot-core/src/main/java/com/bbot/core/registry/BBotSession.java`

In `build()`, after constructing each `AppContext`, check the descriptor's `testDataParser()`. If non-null, call `parser.parse(config.rawConfig())` and pass the result into `AppContext`.

**Quality gate:**
- `mvn test -pl b-bot-core` — all existing tests pass (all current descriptors return `null` from `testDataParser()`).
- New unit test: `BBotSessionTest.build_invokesTestDataParser`.
- New unit test: `BBotSessionTest.build_skipsNullParser`.

#### Step 1.6 — `getBond()` on `TestDataConfig`

**File:** `b-bot-core/src/main/java/com/bbot/core/data/TestDataConfig.java`

Add:
```java
public Bond getBond(String bondId) { ... }
```

Reads from `b-bot.test-data.bonds.{bondId}`. Returns a `Bond` record. Throws `BBotConfigException` if absent.

Also add `"bonds"` to the exclusion filter in `getAllGlobals()`.

**Quality gate:**
- `mvn test -pl b-bot-core` — all existing tests pass (no `bonds` block in core test config yet, so `getBond()` is untested here but doesn't break anything).
- Add `bonds` block to `b-bot-core/src/test/resources/application.conf`.
- New unit test: `TestDataConfigTest.getBond_present`.
- New unit test: `TestDataConfigTest.getBond_absent_throws`.
- Existing test `getAllGlobals_excludesReservedBlocks` — update assertion to also verify `bonds` is excluded.

**Milestone 1 quality gate:**
```
mvn clean verify -pl b-bot-core
```
All existing + new unit tests green. Zero changes to sandbox or regression-template modules. Core JAR is backward-compatible.

---

### Milestone 2 — Bond Catalogue in Config (additive config changes, backward compat)

> **Goal:** Add `bonds` catalogue to all config files. Keep `bond-lists` and `portfolios` unchanged. Both old and new paths work.

#### Step 2.1 — Add `bonds` to `b-bot-core/src/test/resources/application.conf`

Add a `bonds` block mirroring the existing `bond-lists.TEST_BONDS` ISINs:
```hocon
bonds {
  UST-2Y      { isin = "US912828YJ02", description = "UST 4.25% 2034", maturity = "2034-11-15", coupon = 4.250 }
  EUR-HY-XS23 { isin = "XS2346573523", description = "EUR IG Corp 3.5% 2029", maturity = "2029-03-20", coupon = 3.500 }
}
```

**Quality gate:**
- `mvn test -pl b-bot-core` — all tests pass.
- `TestDataConfigTest.getBond_present` / `_absent_throws` now pass with real data.

#### Step 2.2 — Add `bonds` to `b-bot-sandbox/src/test/resources/application.conf`

Add the full bond catalogue for all ISINs referenced in the sandbox's `bond-lists`.

**Quality gate:**
- `mvn test -pl b-bot-sandbox` — all existing Cucumber scenarios pass (bond-lists still present, bonds block is additive).

#### Step 2.3 — Add `bonds` to `pt-blotter-regression-template/src/test/resources/application-mockuat.conf`

Add the full 9-bond catalogue matching all ISINs in `bond-lists` + `portfolios`.

**Quality gate:**
- `mvn test -pl pt-blotter-regression-template -Db-bot.env=mockuat` — all existing scenarios pass (additive only).

**Milestone 2 quality gate:**
```
mvn clean verify
```
All modules green. Bond catalogue co-exists with `bond-lists` and `portfolios`.

---

### Milestone 3 — API Action Path Consolidation

> **Goal:** Eliminate hardcoded API paths from DSLs and config. Every HTTP endpoint becomes a named action.

#### Step 3.1 — Add `api-actions` to `deployment` and `config-service` apps

**Files:** `b-bot-sandbox/src/test/resources/application.conf`, `pt-blotter-regression-template/src/test/resources/application-mockuat.conf`

Add action blocks:
```hocon
deployment {
  api-actions {
    list-deployments { method = "GET", path = "/api/deployments" }
    get-deployment   { method = "GET", path = "/api/deployments/{name}" }
  }
}
config-service {
  api-actions {
    list-config { method = "GET", path = "/api/config" }
  }
}
```

**Quality gate:**
- `mvn compile` — config loads without error (HOCON is additive).

#### Step 3.2 — Add `health-check-action` support to `BBotConfig`

**File:** `b-bot-core/src/main/java/com/bbot/core/config/BBotConfig.java`

Update `getAppHealthCheckPath()` to check for `health-check-action` first. If present, resolve the action name to its path. Fall back to `health-check-path` for backward compat.

**Quality gate:**
- `mvn test -pl b-bot-core` — existing `BBotConfigTest` passes.
- New test: `BBotConfigTest.getAppHealthCheckPath_resolvesFromAction`.

#### Step 3.3 — Migrate config files to `health-check-action`

Replace `health-check-path = "/api/..."` with `health-check-action = "list-..."` in all three conf files.

**Quality gate:**
- `mvn test -pl b-bot-core` — passes (core test conf updated).
- `mvn test -pl b-bot-sandbox` — passes (sandbox conf updated).
- Regression suite passes (mockuat conf updated).

#### Step 3.4 — Refactor `DeploymentDsl` to use `AppContext.getActionPath()`

**Files:** `pt-blotter-regression-template/src/test/java/utils/DeploymentDsl.java`, `b-bot-sandbox/src/test/java/utils/DeploymentDsl.java`

Replace constructor `DeploymentDsl(String apiBase)` with `DeploymentDsl(AppContext ctx)`. Replace all hardcoded `/api/deployments` with `ctx.getActionPath("list-deployments")`.

Update `DeploymentDescriptor.dslFactory()`:
```java
return (ctx, page) -> new DeploymentDsl(ctx);
```

**Quality gate:**
- `mvn test -pl b-bot-sandbox` — all deployment scenarios pass.
- `mvn test -pl pt-blotter-regression-template -Db-bot.env=mockuat` — deployment scenarios pass.
- Grep confirms zero remaining hardcoded `/api/deployments` in Java source (mock servers excluded — they are the server, not the client).

#### Step 3.5 — Refactor `ConfigServiceDsl` similarly

Same pattern as 3.4 for the config-service DSL.

**Quality gate:**
- Grep: zero hardcoded `/api/config` in DSL Java source.
- All tests pass.

#### Step 3.6 — Remove `health-check-path` fallback from `BBotConfig`

Delete the `health-check-path` config key support. Only `health-check-action` is accepted.

**Quality gate:**
- Grep: zero remaining `health-check-path` in `*.conf` files.
- `mvn clean verify` — all modules green.

**Milestone 3 quality gate:**
```
mvn clean verify
```
All API paths defined once in `api-actions`. DSLs are path-agnostic. `health-check-action` is the only supported key.

---

### Milestone 4 — `BlotterTestDataParser` + Domain-Typed Test Data

> **Goal:** Move bond/portfolio parsing from `TestDataConfig` into a blotter-specific parser. Step definitions access typed `BlotterTestData`.

#### Step 4.1 — Create `BlotterTestData` record

**File:** `pt-blotter-regression-template/src/test/java/data/BlotterTestData.java` (new)

```java
public record BlotterTestData(
    Map<String, Bond> bonds,
    Map<String, Portfolio> portfolios,
    Map<String, String> serviceVersions,
    Map<String, String> users,
    Map<String, String> globals
) {}
```

**Quality gate:** Compiles.

#### Step 4.2 — Create `BlotterTestDataParser`

**File:** `pt-blotter-regression-template/src/test/java/data/BlotterTestDataParser.java` (new)

Implements `TestDataParser<BlotterTestData>`. Internally delegates to `TestDataConfig` methods for now (wrap, don't rewrite).

**Quality gate:**
- New unit test: `BlotterTestDataParserTest` — parses mock HOCON, asserts bond catalogue, portfolio resolution.
- `mvn test -pl pt-blotter-regression-template` — new test green.

#### Step 4.3 — Wire parser into `BlotterDescriptor`

**File:** `pt-blotter-regression-template/src/test/java/descriptors/BlotterDescriptor.java`

```java
@Override public TestDataParser<?> testDataParser() {
    return new BlotterTestDataParser();
}
```

**Quality gate:**
- `mvn test -pl pt-blotter-regression-template -Db-bot.env=mockuat` — all scenarios still pass (parser runs at build time, results available via `ctx.getTestData()`, but no step uses it yet).

#### Step 4.4 — Update step definitions to use `BlotterTestData`

**Files:** `RestApiSteps.java`, `BlotterSteps.java` in `pt-blotter-regression-template`

Replace:
```java
testData.resolveBondRef("HYPT_1", "ISIN1")
```
With:
```java
session.context("blotter").getTestData(BlotterTestData.class).bonds().get("UST-2Y").isin()
```

Or introduce a helper:
```java
private BlotterTestData td() {
    return world.session().context("blotter").getTestData(BlotterTestData.class);
}
```

**Quality gate:**
- All step definitions compile.
- Feature files not yet changed — old step patterns still wire to updated Java code.

**Milestone 4 quality gate:**
```
mvn clean verify -pl pt-blotter-regression-template -Db-bot.env=mockuat
```
All scenarios pass. `BlotterTestData` is the active code path. `TestDataConfig` bond methods are still called (wrapped) but will be removed in Milestone 6.

---

### Milestone 5 — Portfolio Bond-Reference Resolution

> **Goal:** Portfolios reference bonds by catalogue ID. The `bond-lists` section is no longer needed.

#### Step 5.1 — Update `PortfolioBond` to carry a `Bond` reference

**File:** `b-bot-core/src/main/java/com/bbot/core/data/PortfolioBond.java`

Change from flat fields to `Bond` delegation:
```java
public record PortfolioBond(Bond bond, long quantity, String side, String currency, long notional, String client) {
    public String isin()        { return bond.isin(); }
    public String description() { return bond.description(); }
    public String maturity()    { return bond.maturity(); }
    public double coupon()      { return bond.coupon(); }
}
```

**Quality gate:**
- `mvn compile -pl b-bot-core` — compiles.
- Existing `TestDataConfigTest.getPortfolio_*` tests — temporarily broken (expected).

#### Step 5.2 — Update `TestDataConfig.getPortfolio()` to resolve bond references

Parsing logic: for each portfolio line, read the `bond` field (e.g. `"UST-2Y"`), look it up in `getBond()`, and construct `PortfolioBond` with the resolved `Bond`.

Fall back to inline `isin`/`description`/`maturity`/`coupon` if `bond` field is absent (backward compat during migration).

**Quality gate:**
- Update core test config: change `TEST_PT` portfolio lines to use `bond = UST-2Y` / `bond = EUR-HY-XS23`.
- `TestDataConfigTest.getPortfolio_parsesBondsInOrder` — update assertions to validate `Bond` delegation.
- `mvn test -pl b-bot-core` — all tests green.

#### Step 5.3 — Migrate portfolio lines in `application-mockuat.conf`

Replace inline `isin = "...", description = "...", maturity = "...", coupon = ...` with `bond = UST-2Y` (etc.) in all portfolio lines.

Add HOCON `${}` substitution for shared cancel lines:
```hocon
_cancel-lines-DE-IT { ... }
CANCEL_DEALER_1   { pt-id = "...", lines = ${b-bot.test-data.portfolios._cancel-lines-DE-IT} }
CANCEL_CUSTOMER_1 { pt-id = "...", lines = ${b-bot.test-data.portfolios._cancel-lines-DE-IT} }
```

**Quality gate:**
- `mvn test -pl pt-blotter-regression-template -Db-bot.env=mockuat` — all scenarios pass.
- Grep: zero `isin = "..."` inside `portfolios` blocks (all resolved from catalogue).

#### Step 5.4 — Remove `bond-lists` from all configs

Delete the entire `bond-lists` block from:
- `b-bot-core/src/test/resources/application.conf`
- `b-bot-sandbox/src/test/resources/application.conf`
- `pt-blotter-regression-template/src/test/resources/application-mockuat.conf`
- `b-bot-core/src/main/resources/reference.conf`

**Quality gate:**
- Grep: zero `bond-lists` in `*.conf`.
- `mvn clean verify` — all modules green.

**Milestone 5 quality gate:**
```
mvn clean verify
```
Bond catalogue is the single source of truth. Portfolios reference by ID. `bond-lists` eliminated.

---

### Milestone 6 — Feature File Migration + Dead Code Removal

> **Goal:** Feature files use bond catalogue IDs. All deprecated code is removed.

#### Step 6.1 — Add new step definitions using bond catalogue IDs

**Files:** Step definitions in both sandbox and regression-template.

Add new steps:
```java
@When("I select the row with bond {string}")
@Then("the row with bond {string} should have status {string}")
@Then("the {string} for bond {string} should be a numeric value")
@Then("the {string} for bond {string} should be blank")
@When("a new inquiry is submitted for bond {string} notional {string} side {string} client {string}")
```

Each resolves `bondId` → `Bond.isin()` via the parser.

**Quality gate:**
- New steps compile.
- Old steps still present (both coexist).

#### Step 6.2 — Migrate feature files

**Files:** All `.feature` files.

Replace:
```gherkin
Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "PENDING"
```
With:
```gherkin
Then the row with bond "UST-2Y" should have status "PENDING"
```

Systematic find-and-replace using the ISIN→bond-ID mapping from the catalogue.

**Quality gate:**
- `mvn clean verify` — all scenarios pass with new vocabulary.
- Grep: zero `from "HYPT_1" field "ISIN` in `.feature` files.

#### Step 6.3 — Remove deprecated code

1. Remove `getBondList()`, `resolveBondRef()` from `TestDataConfig`.
2. Remove old step definitions (`selectRowByBondRef`, `rowByBondRefShouldHaveStatus`, etc.).
3. Remove `TestDataConfigTest` bond-list tests (replaced by bond-catalogue tests).

**Quality gate:**
- `mvn clean verify` — all modules green.
- Grep: zero `resolveBondRef` or `getBondList` in Java source.

#### Step 6.4 — Deprecate and hollow out `TestDataConfig`

Move remaining domain-specific methods to `BlotterTestDataParser`. Keep only generic utilities in `TestDataConfig`:
- `getGlobal()` / `getAllGlobals()`
- `getTemplatePath()`

Mark `getServiceVersion()`, `getUser()`, `getPortfolio()` as `@Deprecated` with `forRemoval = true`.

**Quality gate:**
- `mvn clean verify` — all green.
- Zero non-deprecated callers of the deprecated methods.

**Milestone 6 quality gate:**
```
mvn clean verify
```
Feature files use bond IDs. All dead code removed. `TestDataConfig` is a thin generic utility. `BlotterTestDataParser` owns all blotter domain logic.

---

### Milestone 7 — Final Hardening

> **Goal:** Documentation, config validation, regression confidence.

#### Step 7.1 — Update `reference.conf` comments and HOCON structure documentation

Replace the commented-out `bond-lists` examples with the new `bonds` catalogue and `health-check-action` patterns.

#### Step 7.2 — Add config-load validation

In `BlotterTestDataParser.parse()`, validate at parse time:
- Every portfolio line's `bond` reference exists in the catalogue.
- No duplicate ISINs in the catalogue.
- `health-check-action` references a valid action name.

Fail-fast with clear `BBotConfigException` messages.

**Quality gate:**
- New unit test: `BlotterTestDataParserTest.parse_missingBondRef_throws`.
- New unit test: `BlotterTestDataParserTest.parse_duplicateIsin_throws`.

#### Step 7.3 — Update design docs

Update `MODULARISATION_DESIGN.md`, `BLOTTER_DESIGN.md`, `README.md` to reflect:
- Bond catalogue as the canonical data source.
- `health-check-action` pattern.
- `TestDataParser` extension point on `AppDescriptor`.

#### Step 7.4 — Full regression sweep

```
mvn clean verify                                           # core + sandbox
mvn clean verify -pl pt-blotter-regression-template -Db-bot.env=mockuat  # regression
```

**Milestone 7 quality gate:**
All modules green. Documentation consistent with code. Config validation catches misconfigurations at load time.

---

### Summary — Milestone Quality Gate Matrix

| Milestone | Scope | Breaking Changes | Quality Gate Command |
|---|---|---|---|
| **M1** — Core Abstractions | `b-bot-core` only | None | `mvn clean verify -pl b-bot-core` |
| **M2** — Bond Catalogue in Config | All config files | None (additive) | `mvn clean verify` |
| **M3** — API Action Consolidation | Config + DSLs | `health-check-path` removed | `mvn clean verify` |
| **M4** — BlotterTestDataParser | regression-template | None | `mvn clean verify -pl pt-blotter-regression-template` |
| **M5** — Portfolio Bond References | All modules | `bond-lists` removed, `PortfolioBond` signature changed | `mvn clean verify` |
| **M6** — Feature File Migration | Feature files + steps | Old step patterns removed | `mvn clean verify` |
| **M7** — Final Hardening | Docs + validation | None | `mvn clean verify` (full) |

Each milestone is independently shippable. If work is interrupted after any milestone, the codebase is in a consistent, fully-tested state.

