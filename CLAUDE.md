
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
   - One class per logical page/component under `src/test/java/pages/`.
   - Keep locator definitions in the Page class; keep assertions in step definitions.

4. **Feature files are the source of truth for acceptance criteria.**
   - Every scenario must map to a real, observable user behaviour.
   - Tag slow/flaky scenarios with `@ticking` so they can be isolated.

5. **All JavaScript runs through `window.agGridProbes` — never inline JS strings.**
   - Add new browser-side logic to the appropriate probe module in `src/test/js/probes/`.
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
The API is discovered via React fibre traversal — see `src/test/js/probes/api-discovery.js`.
This logic is encapsulated in `bundle.js` and accessible as `window.agGridProbes.gridApi.*`.

### Filter model formats (AG Grid v33)
- Text columns: `{ filterType: 'text', type: 'contains', filter: value }`
- Set filter columns (e.g. `instrument`): `{ filterType: 'set', values: [value] }`
- Java pattern: try `applyTextFilter` first, catch `PlaywrightException`, retry with `applySetFilter`.

---

## Probe Architecture

### How it works
1. `src/test/js/probes/bundle.js` is an IIFE that registers `window.agGridProbes`.
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
1. Edit the relevant module in `src/test/js/probes/` (e.g. `dom-probes.js`).
2. Mirror the change in `bundle.js` (the IIFE must stay in sync).
3. Add/update the Jest test in `src/test/js/__tests__/`.
4. Verify with `cd src/test/js && npm test` (requires Node.js 18+).
5. No Java changes needed — the new function is immediately available as `window.agGridProbes.*`.

---

## Directory Layout
```
b-autobot/
├── CLAUDE.md
├── BLOTTER_DESIGN.md       # Full design doc — blotter, config service, deployment dashboard
├── pom.xml
└── src/test/
    ├── java/
    │   ├── model/          # Jackson POJOs (Trade, TradePortfolio)
    │   ├── pages/          # Page Object Model classes
    │   ├── runners/        # Cucumber test runners
    │   ├── stepdefs/       # Step definition classes (BondBlotterSteps, ConfigServiceSteps, DeploymentSteps, …)
    │   └── utils/          # Helpers — BlotterDsl, BlotterDevServer, ConfigServiceDsl, ConfigDevServer,
    │                       #           DeploymentDsl, DeploymentDevServer, MockBlotterServer,
    │                       #           MockConfigServer, MockDeploymentServer, GridHarness,
    │                       #           TickingCellHelper, ProbesLoader, PlaywrightManager
    ├── js/                 # JavaScript probe workspace (npm + Jest)
    │   ├── package.json
    │   ├── jest.config.js
    │   ├── probes/         # Individual probe modules + bundle.js
    │   └── __tests__/      # Jest unit tests (jsdom)
    ├── webapp/             # PT-Blotter React + Vite source
    ├── webapp-config/      # Config Service React + Vite source
    ├── webapp-deployment/  # Deployment Dashboard React + Vite source
    └── resources/
        ├── features/               # .feature files (Gherkin)
        │   ├── BondBlotter.feature      # 39 scenarios (M0–M8 + precondition)
        │   ├── ConfigService.feature    # 14 scenarios
        │   ├── Deployment.feature       # 15 scenarios
        │   ├── finance_demo.feature
        │   └── PortfolioRegression.feature
        ├── config-service-ui/      # Vite build output (git-committed)
        ├── deployment-ui/          # Vite build output (git-committed)
        ├── wiremock/__files/       # WireMock stubs + blotter Vite build (git-committed)
        └── cucumber.properties
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
# All 66 scenarios (headless Chromium)
mvn verify

# Full suite with blotter Vite rebuild
mvn verify -Dblotter.build.skip=false   # → 66/66

# Headed browser — opens a real Chromium window
mvn verify -DHEADLESS=false

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
cd src/test/js
npm install        # once — requires Node.js 18+
npm test           # run all probe unit tests
npm run test:coverage
```
