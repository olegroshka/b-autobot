
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

---

## AG Grid Specific Knowledge

### Cell Locator Pattern
```java
// Preferred: attribute-based, survives row re-ordering
Locator cell = page.locator(".ag-center-cols-container [row-index='0'] [col-id='price']");

// For header cells
Locator header = page.locator(".ag-header-cell[col-id='symbol']");
```

### Ticking / Live-Price Cells
AG Grid's Finance Demo updates price cells at ~200–500 ms intervals.
Key challenges:
- DOM value is stale by the time you read it.
- `ag-cell-data-changed` CSS class is applied for ~400 ms on each tick — use it as a signal.
- The grid exposes `window.agGrid` or a ref; JS evaluation can reach `api.getDisplayedRowAtIndex(n)`.

**Approved strategy — see `TickingCellHelper`:**
| Need | Approach |
|---|---|
| Wait for a value to change | `page.waitForFunction()` polling selector + value |
| Assert value is in a numeric range | Parse text → `assertThat(value).isBetween(min, max)` |
| Detect that a tick happened | Wait for `ag-cell-data-changed` class to appear on cell |
| Stabilize before reading | Wait for `ag-cell-data-changed` class to **disappear** (animation done) |

### Virtualization
AG Grid only renders visible rows. If a row scrolls out of view its DOM node is recycled.
- Always scroll the target row into view before asserting: `cell.scrollIntoViewIfNeeded()`.
- Use the grid's own filter/sort to bring rows to top rather than relying on absolute row-index.

---

## Directory Layout
```
b-autobot/
├── CLAUDE.md
├── pom.xml
└── src/test/
    ├── java/
    │   ├── pages/          # Page Object Model classes
    │   ├── runners/        # Cucumber test runners
    │   ├── stepdefs/       # Step definition classes
    │   └── utils/          # Helpers (TickingCellHelper, etc.)
    └── resources/
        ├── features/       # .feature files (Gherkin)
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
```bash
# All tests
mvn test

# Only ticking scenarios
mvn test -Dcucumber.filter.tags="@ticking"

# Headed browser (debug)
mvn test -DHEADLESS=false
```
