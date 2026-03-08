
# b-autobot — Project Memory & Rules

## Project Context
Playwright-based BDD test automation suite targeting the **AG Grid React Finance Demo**.
Stack: Java 21 · Playwright for Java · Cucumber 7 · Jackson · JUnit 5 · Maven.

---

## Mandatory Rules

1. **Prioritize Playwright Locators over CSS selectors for AG Grid stability.**
   - Prefer `page.locator("[role='row'][row-index='N'] [col-id='colName']")` over brittle nth-child CSS.
   - Use `page.getByRole()` for buttons, inputs, and dialogs.
   - Never hard-code pixel positions or rely on `:nth-child` for grid rows.

2. **No `Thread.sleep()` — ever.**
   - Use `page.waitForFunction()` for dynamic DOM conditions.
   - Use `Playwright assertThat()` (which has built-in auto-retry) for value assertions.
   - Use `page.waitForTimeout(ms)` only as an absolute last resort (log a TODO comment).

3. **Page Object Model (POM) for all page interactions.**
   - One class per logical page/component under `b-bot-sandbox/src/test/java/pages/`.
   - Keep locator definitions in the Page class; keep assertions in step definitions.

4. **Feature files are the source of truth for acceptance criteria.**
   - Every scenario must map to a real, observable user behaviour.
   - Tag slow/flaky scenarios with `@ticking` so they can be isolated.

5. **All JavaScript runs through `window.agGridProbes` — never inline JS strings.**
   - Add new browser-side logic to the appropriate probe module in `b-bot-sandbox/src/test/js/probes/`.
   - Regenerate (or manually update) `bundle.js` so the change is picked up by Playwright.
   - Write a Jest unit test for the new probe function before wiring it into Java.
   - Never use `//` single-line comments inside Java string literals that contain JS
     (concatenated strings have no newlines, so `//` comments out the rest of the function body).

---

## AG Grid Specific Knowledge

### Cell Locator Pattern
```java
// Preferred: attribute-based, survives row re-ordering
Locator cell = page.locator(".ag-center-cols-container [row-index='0'] [col-id='price']");

// For header cells
Locator header = page.locator(".ag-header-cell[col-id='symbol']");
```

### Column identifiers (Finance Demo)
The live Finance Demo uses these `col-id` values: `ticker`, `instrument`, `totalValue`, `quantity`, `purchasePrice`.
The `ticker` column uses a custom renderer that appends the company name — `textContent` returns e.g. `"AAPLApple Inc"`.
Always use `contains()` (not `equals()`) when asserting ticker cell text.

### Ticking / Live-Price Cells
AG Grid's Finance Demo updates price cells at ~200–500 ms intervals.
Key challenges:
- DOM value is stale by the time you read it.
- `ag-cell-data-changed` CSS class is applied for ~400 ms on each tick — use it as a signal.

**Approved strategy — see `TickingCellHelper` + `window.agGridProbes.ticking`:**
| Need | Approach |
|---|---|
| Wait for a value to change | `waitForFunction` → `agGridProbes.ticking.hasCellValueChanged(sel, initial)` |
| Assert value is in a numeric range | Parse text → `assertThat(value).isBetween(min, max)` |
| Detect that a tick happened | `agGridProbes.ticking.isCellFlashing(sel)` |
| Stabilize before reading | `agGridProbes.ticking.isCellStable(sel)` |

### Virtualization
AG Grid only renders visible rows. If a row scrolls out of view its DOM node is recycled.
- Always scroll the target row into view before asserting: `cell.scrollIntoViewIfNeeded()`.
- Use `GridHarness.findRowByCellValue()` for virtualization-safe row lookup (3-phase strategy).

### AG Grid API Discovery (React Finance Demo)
`window.gridApi` is **not** set by the Finance Demo's React app.
The API is discovered via React fibre traversal — see `b-bot-sandbox/src/test/js/probes/api-discovery.js`.
This logic is encapsulated in `bundle.js` and accessible as `window.agGridProbes.gridApi.*`.

### Filter model formats (AG Grid v33)
- Text columns: `{ filterType: 'text', type: 'contains', filter: value }`
- Set filter columns (e.g. `instrument`): `{ filterType: 'set', values: [value] }`
- Java pattern: try `applyTextFilter` first, catch `PlaywrightException`, retry with `applySetFilter`.

---

## Probe Architecture

### How it works
1. `b-bot-core/src/main/resources/js/probes/bundle.js` is an IIFE that registers `window.agGridProbes`.
2. `ProbesLoader.java` reads `bundle.js` from the classpath (lazy, cached).
3. `PlaywrightManager.initContext()` injects it via `ctx.addInitScript(ProbesLoader.load())`.
4. Every page opened in that context has `window.agGridProbes` available before navigation.

### Probe namespaces
| Namespace | Key functions |
|---|---|
| `agGridProbes.dom` | `getVisibleCellTexts`, `findRowIndexByText`, `isRowInDom`, `getLastDomRowIndex`, `areAllVisibleCellsContaining`, `isCellFlashing`, `isCellStable`, `hasCellValueChanged` |
| `agGridProbes.gridApi` | `findRowIndexByDataValue`, `ensureRowVisible`, `isApiAvailable` |
| `agGridProbes.filter` | `applyTextFilter`, `applySetFilter`, `clearAllFilters` |
| `agGridProbes.scroll` | `scrollToTop`, `scrollDown`, `isAtBottom` |
| `agGridProbes.ticking` | `isCellFlashing`, `isCellStable`, `hasCellValueChanged`, `getCellText` |

### Modifying probes
1. Edit the relevant module in `b-bot-sandbox/src/test/js/probes/` (e.g. `dom-probes.js`).
2. Mirror the change in `bundle.js` (the IIFE must stay in sync).
3. Add/update the Jest test in `b-bot-sandbox/src/test/js/__tests__/`.
4. Verify with `cd b-bot-sandbox/src/test/js && npm test` (requires Node.js 18+).
5. No Java changes needed — the new function is immediately available as `window.agGridProbes.*`.

---

## Directory Layout
```
b-autobot/
├── CLAUDE.md
├── BLOTTER_DESIGN.md               # Full design doc — blotter, config service, deployment dashboard
├── MODULARISATION_DESIGN.md        # Multi-module architecture design record (M1–M12)
├── IMPLEMENTATION_PLAN.md          # Industrialisation plan (M7–M12) — all COMPLETE
├── .github/workflows/ci.yml        # GitHub Actions CI (core + sandbox + nightly template)
├── pom.xml                         # Parent aggregator — version management for all modules
│
├── b-bot-core/                     # Publishable library (no Cucumber dependency)
│   └── src/main/
│       ├── java/com/bbot/core/
│       │   ├── BrowserLifecycle    # Interface (M10) — mockable browser lifecycle
│       │   ├── CellAssertions      # Interface (M10) — mockable ticking-cell assertions
│       │   ├── GridQuery           # Interface (M10) — mockable AG Grid row queries
│       │   ├── PlaywrightManager   # @Deprecated statics (M11) — use BBotSession
│       │   ├── GridHarness         # implements GridQuery; config via constructor (M11)
│       │   ├── TickingCellHelper   # Live-ticking cell wait/assert helpers
│       │   ├── ProbesLoader        # Injects window.agGridProbes bundle
│       │   ├── NumericComparator   # UI vs API value comparison (BigDecimal)
│       │   ├── exception/          # BBotException hierarchy (M8a) — 6 typed exceptions
│       │   ├── auth/               # SsoAuthConfig, SsoAuthManager, ClientCredentialsAuth (M13)
│       │   ├── config/BBotConfig   # HOCON layered config (5-layer loading)
│       │   ├── registry/           # AppDescriptor / AppContext / BBotRegistry
│       │   │   ├── BBotSession     # Immutable session — instance API (M11)
│       │   │   └── BBotRegistry    # @Deprecated statics delegate to BBotSession (M11)
│       │   └── rest/               # RestClient (M10), RestProbe, RestResponse (M9)
│       │       ├── ScenarioContext # Per-scenario instance-based state (M11)
│       │       ├── ScenarioState   # @Deprecated thread-local, delegates to ScenarioContext
│       │       ├── AuthStrategy    # Bearer token / no-auth (M9)
│       │       ├── RetryPolicy     # Exponential backoff record (M9)
│       │       └── HttpClientFactory # Shared HttpClient factory (M9)
│       └── resources/
│           ├── js/probes/bundle.js # JS probe bundle (on classpath for ProbesLoader)
│           └── reference.conf      # Core defaults (browser, timeouts, grid settings)
│
├── b-bot-sandbox/                  # Demo & regression suite (all 66 scenarios)
│   └── src/test/
│       ├── java/
│       │   ├── descriptors/        # BlotterAppDescriptor, ConfigServiceDescriptor, DeploymentDescriptor
│       │   ├── model/              # Jackson POJOs (Trade, TradePortfolio)
│       │   ├── pages/              # FinanceDemoPage (AG Grid Finance Demo POM)
│       │   ├── runners/            # JUnit 5 @Suite runner
│       │   ├── stepdefs/           # TestWorld (M11 PicoContainer), *Steps, Hooks
│       │   └── utils/              # BlotterDsl, ConfigServiceDsl, DeploymentDsl,
│       │                           # MockBlotterServer, MockConfigServer, MockDeploymentServer,
│       │                           # BlotterDevServer, ConfigDevServer, DeploymentDevServer
│       ├── js/                     # JavaScript probe workspace (npm + Jest)
│       │   ├── probes/             # Individual probe modules + bundle.js (source)
│       │   └── __tests__/          # Jest unit tests (jsdom)
│       └── resources/
│           ├── features/           # BondBlotter (39), ConfigService (14), Deployment (15), …
│           ├── wiremock/__files/   # Pre-built blotter Vite assets (git-committed)
│           ├── config-service-ui/  # Pre-built Config Service UI (git-committed)
│           └── deployment-ui/      # Pre-built Deployment Dashboard UI (git-committed)
│
└── pt-blotter-regression-template/ # Copy-adapt starter for real-system consumers (24 scenarios)
    └── src/test/
        ├── java/
        │   ├── descriptors/        # BlotterDescriptor, ConfigServiceDescriptor, DeploymentDescriptor
        │   ├── stepdefs/           # TestWorld (M11 PicoContainer), Hooks, *Steps, RestApiSteps
        │   └── utils/PtBlotterDsl.java
        └── resources/
            ├── application.conf             # Base config + commented overrides
            ├── application-devserver.conf   # Points at localhost:9099
            └── features/Smoke.feature
```

---

## Authentication Conventions (M13)

### Auth classes
| Class | Package | Purpose |
|-------|---------|---------|
| `SsoAuthConfig` | `com.bbot.core.auth` | Immutable record — parses `b-bot.auth` HOCON block |
| `SsoAuthManager` | `com.bbot.core.auth` | Orchestrator — interactive login, storageState, OAuth |
| `ClientCredentialsAuth` | `com.bbot.core.auth` | OAuth2 `client_credentials` `AuthStrategy` impl |
| `StorageStateAuth` | `com.bbot.core.rest` | `AuthStrategy` that reads Playwright storageState JSON |
| `BBotAuthException` | `com.bbot.core.exception` | Auth-specific exception (expired session, OAuth failure) |

### Rules
1. **Never commit secrets.** Use HOCON env-var substitution: `clientId = ${?B_BOT_CLIENT_ID}`.
2. **storageState files go in `target/`** (gitignored). Never commit auth state.
3. **mode=none is always the default** in `reference.conf`. Existing mock-env tests are unaffected.
4. **401/403 with active auth throws `BBotAuthException`** (not `BBotRestException`) with an actionable hint.
5. **Interactive login uses its own Playwright instance** — isolated from the test suite's `PlaywrightManager`.
6. **`ensureAuthenticated()` is idempotent** — safe to call multiple times; returns immediately if session is valid.

### Config keys (`b-bot.auth.*`)
```hocon
b-bot.auth {
  mode                    = none | interactive | storageState | auto | clientCredentials
  storageStatePath        = "target/auth/storage-state.json"
  sessionTtl              = 4h
  loginUrl                = ""
  loginTimeout            = 120s
  loginSuccessIndicator   = pause | urlContains:<pattern> | element:<selector>
  tokenUrl                = ""
  clientId                = ""
  clientSecret            = ""
  scope                   = ""
  refreshOn401            = true
}
```

---

## Library Versions (pinned)
| Library | Version |
|---|---|
| Java | 21 |
| Playwright for Java | 1.49.0 |
| Cucumber | 7.18.1 |
| Jackson Databind | 2.17.2 |
| JUnit Jupiter | 5.10.3 |
| JUnit Platform Suite | 1.10.3 |
| Maven Surefire Plugin | 3.5.0 |

---

## Running Tests

### Java / Cucumber (Maven)
```bash
# All 66 scenarios (headless Chromium, committed assets — no Node needed)
mvn verify

# Full suite with blotter Vite rebuild
mvn verify -Dblotter.build.skip=false   # → 66/66

# Headed browser — opens a real Chromium window
mvn verify -Db-bot.browser.headless=false    # preferred (HOCON config)
mvn verify -DHEADLESS=false                  # legacy alias (still works)

# Only ticking scenarios
mvn verify -Dcucumber.filter.tags="@ticking"

# Self-contained WireMock scenarios only (no internet needed)
mvn verify -Dcucumber.filter.tags="@portfolio and not @external"

# Deployment / config service (no browser needed)
mvn verify -Dcucumber.filter.tags="@deployment"
mvn verify -Dcucumber.filter.tags="@config-service"
```

### JavaScript probes (Jest)
```bash
cd b-bot-sandbox/src/test/js
npm install        # once — requires Node.js 18+
npm test           # run all probe unit tests
npm run test:coverage
```
