# PT-Blotter Design — Approach Analysis

Fixed income bond portfolio trading blotter: design options for a realistic mock
that (a) demonstrates real trading workflow and (b) drives b-autobot BDD regression
testing with a clean DSL.

---

## What the blotter must do

### Workflow (the thing we are actually testing)

```
ION channel / REST API
        │
        ▼
  Inquiry arrives  ──► PT-Blotter row (status: PENDING)
        │
        ▼
  Trader selects row(s)
        │
        ▼
  Picks ref source  (TW / CP+ / CBBT)  +  convention (Price / Spread)
  Picks side        (Bid / Offer)
  Enters skew       (+N cents  or  +N bp)
        │
        ▼
  Presses APPLY  ──►  Applied Bid / Applied Offer recalculated on row
        │
        ▼
  Presses SEND   ──►  Row status → QUOTED,  row locked (no further edits)
```

### Column layout

| Column group | Columns |
|---|---|
| Identity | Checkbox, ISIN, Description, Maturity, Coupon, Notional, Side, Client |
| Status | Status (PENDING / QUOTED / DONE / MISSED) |
| TradeWeb (TW) | TW Bid *(ticking)*, TW Ask *(ticking)* |
| Bloomberg CP+ | CP+ Bid *(ticking)*, CP+ Ask *(ticking)* |
| Bloomberg CBBT | CBBT Bid *(ticking)*, CBBT Ask *(ticking)* |
| Skew controls | Ref Source (dropdown), Convention (Price/Spread), Ref Side (Bid/Ask), Skew Δ |
| Applied prices | Applied Bid, Applied Ask |

### Mock data sources
- Price simulation: `setInterval` with Gaussian noise around mid — no external feed needed.
- ION inquiry: `POST /api/inquiry` via WireMock stub (or a "Simulate ION" button).
- Quote submission: `POST /api/inquiry/{id}/quote` — WireMock confirms, updates row status.

---

## Approach 1 — Vanilla AG Grid + WireMock static files

### Stack
- **AG Grid Community Edition** loaded from a local npm bundle or embedded CDN-equivalent
- Pure HTML + vanilla JavaScript — no React, no Webpack, no build step for the page itself
- Served as a **WireMock static file** (`src/test/resources/wiremock/__files/blotter.html`)
- `setInterval`-driven price simulation in-page
- REST endpoints (`POST /api/inquiry`, `POST /api/inquiry/{id}/quote`) as WireMock stubs
- ION simulation: a "Simulate RFQ" button that calls the same POST stub

### How it fits into the existing Maven project

```
src/test/
├── resources/
│   ├── wiremock/
│   │   ├── __files/
│   │   │   └── blotter.html          ← served at http://localhost:{port}/blotter.html
│   │   └── mappings/
│   │       ├── inquiry-post.json
│   │       └── inquiry-quote-post.json
│   └── features/
│       └── BondBlotter.feature       ← new Cucumber scenarios
└── java/
    └── stepdefs/
        └── BondBlotterSteps.java     ← new step definitions
```

WireMock already starts in `@BeforeAll` (Hooks.java). Serving static HTML costs zero
additional infrastructure — just drop `blotter.html` in `__files/`.

### Pros

| # | Pro |
|---|---|
| 1 | **Zero new infrastructure.** WireMock + existing Maven lifecycle. No separate server process to manage. |
| 2 | **No build toolchain for the page.** AG Grid CE loads from a bundled JS file (copy once from npm). No Webpack/Vite/node_modules involved in CI. |
| 3 | **Maximum determinism.** Everything mocked, no internet needed, reproducible in CI. |
| 4 | **Familiar locator patterns.** Same `.ag-center-cols-container [row-index='N'] [col-id='X']` patterns, same probes, same `GridHarness`. Zero friction for b-autobot. |
| 5 | **Fastest to iterate.** Change `blotter.html`, refresh browser — instant feedback. |
| 6 | **Self-contained.** The entire mock lives inside the Maven project. Clone + `mvn verify` works everywhere. |
| 7 | **AG Grid CE is fully capable.** Row selection, custom cell renderers, `enableCellChangeFlash`, `applyTransactionAsync` for ticking — all free, all in vanilla JS. |

### Cons

| # | Con |
|---|---|
| 1 | **Vanilla JS maintenance.** A complex workflow (dropdown state, APPLY logic, SEND transitions) gets verbose without a component framework. Manageable with clean modules but not as ergonomic as React. |
| 2 | **No hot-module reload.** Edit → hard refresh. Slow when iterating on the page layout. |
| 3 | **Cell editing UX.** Custom in-cell editors (skew input, ref source dropdown) require more boilerplate in vanilla JS than in React. |
| 4 | **AG Grid vanilla API differences.** Older/community AG Grid docs mix framework examples; vanilla JS examples are fewer. |

### Complexity: LOW. Fits current project perfectly.

---

## Approach 2 — React + AG Grid CE + Vite (Finance Demo pattern)

### Stack
- **React 18 + AG Grid React component** (`ag-grid-react`)
- **Vite** for local development (HMR) and production build
- **Express + ws** (or Vite's own dev server) for simulated WebSocket price feed
- Build output (`dist/`) copied into `src/test/resources/static/` and served from embedded Jetty
- WireMock stubs handle REST endpoints (`/api/inquiry`, `/api/inquiry/:id/quote`)

### How it fits

```
src/
├── main/web/                         ← React app source (new top-level directory)
│   ├── src/
│   │   ├── BlotterGrid.tsx
│   │   ├── PriceSimulator.ts
│   │   └── App.tsx
│   ├── package.json
│   └── vite.config.ts
└── test/
    ├── resources/
    │   └── static/                   ← Vite build output (git-committed or built in Maven)
    └── java/
        └── utils/
            └── BlotterServer.java    ← embedded Jetty serving static/
```

Maven `generate-test-resources` phase runs `npm run build`, output lands in
`src/test/resources/static/`, Jetty starts in `@BeforeAll`.

### Pros

| # | Pro |
|---|---|
| 1 | **React component model.** Complex workflow UI (dropdown → input → button state) is much cleaner as composable React components than vanilla JS. |
| 2 | **AG Grid React component.** First-class citizen — same as the Finance Demo we already target. Identical internal structure, same Playwright locators work immediately. |
| 3 | **HMR during development.** `npm run dev` gives instant feedback when building the blotter UI. |
| 4 | **Most realistic mock.** Mirrors how real production React trading UIs are built. Maximises the value of the tests as regression evidence for a production system. |
| 5 | **Vite is tiny.** Build toolchain is minimal compared to Webpack-era setups. `npm run build` in ~5 s. |
| 6 | **Extensible.** Adding a new column, a new ref source, a new button is a one-file React component change. |

### Cons

| # | Con |
|---|---|
| 1 | **Node.js required in CI.** Every build agent needs Node.js + npm. Adds a prerequisite not currently in the project. |
| 2 | **Two runtimes to manage.** JVM (Maven/WireMock/Jetty) + Node.js (Vite build). Maven exec plugin can call `npm run build` but adds complexity. |
| 3 | **Build step on every change.** Need to rebuild and re-deploy for test changes to take effect. Adds 10–30 s to the test-compile cycle. |
| 4 | **Committed build output dilemma.** Either commit `dist/` to git (messy) or rebuild it in Maven (requires Node.js in CI). |
| 5 | **More moving parts = more points of failure.** Broken npm dep, Vite version clash, or Jetty port conflict breaks the test suite for non-JS reasons. |
| 6 | **React fibre still needs to be traversed.** The Finance Demo pattern means AG Grid API still isn't on `window` — we already have that solved, but it's worth noting the pattern continues. |

### Complexity: MEDIUM. Best balance of realism and engineering effort.

---

## Approach 3 — FINOS VUU (fork / adapt)

### Stack
- **VUU** (contributed by UBS, FINOS incubating): TypeScript React frontend + Scala/Java backend
- Custom React grid built for 1M+ rows / 100k ticks per second
- WebSocket (ViewServer protocol) for streaming
- Server-side filtering, sorting, virtualization
- Would require: running Scala ViewServer, adapting VUU blotter layouts, bridging WireMock

### Research findings
- VUU's grid is **not AG Grid** — it is a custom React component optimised for extreme data volumes
- Production use at UBS for real trading blotter workloads
- Incubating FINOS project — active but API is not stable
- Full stack: Java/Scala ViewServer + TypeScript React frontend
- Demo live at `demo.vuu.finos.org`

### Pros

| # | Pro |
|---|---|
| 1 | **Production-proven for exactly this use case.** Built by UBS specifically for trading blotter workflows. |
| 2 | **Extreme scalability.** Server-side streaming, virtualization — can handle real bond universe sizes (100k ISINs). |
| 3 | **Pre-built trading UI patterns.** Column groups, instrument search, blotter layouts already designed for FI trading. |
| 4 | **FINOS backing.** Open-source, governance, active community around FinTech use cases. |

### Cons

| # | Con |
|---|---|
| 1 | **Not AG Grid.** Our entire probe architecture (`window.agGridProbes`, `GridHarness`, locator patterns) targets AG Grid DOM structure. VUU's custom grid has entirely different DOM — all probes need rewriting. |
| 2 | **Scala backend required.** VUU's ViewServer is Scala. Adding a Scala process to the Maven test lifecycle is a significant infrastructure change. |
| 3 | **Incubating / unstable API.** VUU is not v1.0. Upgrading risks breaking the blotter layer. |
| 4 | **Forking is a maintenance burden.** VUU is a full application, not a library. Customising it means diverging from upstream and absorbing all maintenance. |
| 5 | **Massively overengineered for a mock.** We need a test target, not a production trading system. VUU's 1M-row / 100k-tick capability is irrelevant at mock scale. |
| 6 | **Completely different tech stack.** TypeScript + Scala + ViewServer protocol — zero code reuse from the existing b-autobot framework. |

### Complexity: HIGH. Misaligned with goals.

---

## Side-by-side comparison

| Criterion | Approach 1: Vanilla AG Grid | Approach 2: React + Vite | Approach 3: FINOS VUU |
|---|---|---|---|
| New infrastructure | None | Node.js + embedded Jetty | Scala + Node.js |
| Build step | None (static HTML) | `npm run build` | Scala sbt + npm |
| AG Grid locator reuse | Full — identical DOM | Full — identical DOM | None — different grid |
| Probe reuse | Full | Full | None |
| Time to first running mock | ~1 day | ~2–3 days | ~1–2 weeks |
| UI maintainability | Moderate (vanilla JS) | High (React components) | High (but diverged fork) |
| Realistic workflow | Yes | Yes | Yes |
| CI simplicity | High | Medium | Low |
| Bond trading fidelity | Sufficient for testing | Very good | Production-grade (overkill) |
| Recommended for this project | **Yes (MVP)** | **Yes (evolved)** | No |

---

## Recommendation (revised)

### Why Approach 1 (vanilla HTML) was initially attractive but is the wrong call

The original argument for Approach 1 rested on "zero new infrastructure — no Node.js needed."
That argument is **already invalid**: the project added `src/test/js/` with `package.json`
and Jest in the previous session, making Node.js a first-class prerequisite.  The main
pillar of Approach 1 is gone.

What you actually get with a monolithic `blotter.html`:

- **AG Grid vanilla cell renderers** for ticking cells, status badges, dropdowns — these
  exist in AG Grid CE vanilla JS but are significantly more boilerplate than their React
  equivalents.  AG Grid's docs and examples are 90% React/Angular biased.
- **Complex per-row state** (dropdown for ref source + convention + side, number input for
  skew, derived applied prices) spread across `<script>` tags with no component boundaries —
  a spaghetti attractor.
- **Re-inventing React patterns badly** — because what the blotter needs (component state,
  controlled inputs, derived computed values, row-level status machine) is exactly what
  React was designed for.

### Chosen approach: Approach 2 — React + AG Grid CE + Vite, from day 1

| Prerequisite already met | Why |
|---|---|
| Node.js | Required by `src/test/js/` Jest probe tests |
| npm exec in Maven | `exec-maven-plugin` already in pom.xml |
| AG Grid DOM patterns | React AG Grid renders identical `[row-index]`/`[col-id]` DOM |
| WireMock static file serving | `__files/` folder already used — serves Vite `dist/` with no extra server |

The serving architecture requires **no new Java server**:

```
mvn test-compile
  └── exec-maven-plugin: npm run build  (Vite)
        └── dist/ → src/test/resources/wiremock/__files/blotter/

mvn verify
  └── @BeforeAll: MockBlotterServer.start()  (WireMock, already exists)
        └── Playwright navigates to http://localhost:{wiremockPort}/blotter/index.html
        └── REST stubs at /api/inquiry, /api/inquiry/{id}/quote
```

### Approach 3: Do not use

VUU is excellent engineering but it is a full trading platform, not a test target.
The grid is not AG Grid (all probe work would need rewriting) and the Scala backend
is a disproportionate infrastructure commitment for a mock. Not the right tool.

---

## Proposed column schema

```
Checkbox | ISIN | Description | Maturity | Coupon | Notional | Side | Client
Status: PENDING / QUOTED / DONE / MISSED
TW Bid* | TW Ask*
CP+ Bid* | CP+ Ask*
CBBT Bid* | CBBT Ask*
Ref Source (dropdown: TW/CP+/CBBT) | Convention (Price/Spread) | Ref Side (Bid/Ask) | Skew Δ
Applied Bid | Applied Ask

* ticking cells — ag-cell-data-changed flash enabled
```

## Proposed REST contract (WireMock stubs)

```
POST /api/inquiry
  Request:  { isin, description, notional, side, client }
  Response: 201 { inquiry_id, status: "PENDING", ... }

POST /api/inquiry/{id}/quote
  Request:  { applied_bid, applied_ask, ref_source, convention, skew }
  Response: 200 { inquiry_id, status: "QUOTED", timestamp }

GET  /api/inquiries
  Response: 200 [ ...all current inquiries ]

POST /api/inquiry/unknown-isin  (WireMock priority stub)
  Response: 404 { error: "ISIN not found" }
```

## Implementation plan (Approach 2 selected)

### Directory layout

```
src/
├── test/
│   ├── webapp/                          ← Vite + React blotter app
│   │   ├── src/
│   │   │   ├── App.tsx                  ← root layout + toolbar (APPLY / SEND / CLEAR)
│   │   │   ├── BlotterGrid.tsx          ← AG Grid component + column definitions
│   │   │   ├── PriceSimulator.ts        ← setInterval Gaussian price feed
│   │   │   ├── SkewControls.tsx         ← per-row ref source / convention / skew input
│   │   │   ├── api.ts                   ← fetch wrappers for /api/inquiry endpoints
│   │   │   └── types.ts                 ← Inquiry, RefSource, Convention, Status enums
│   │   ├── index.html
│   │   ├── package.json
│   │   └── vite.config.ts               ← proxy /api → WireMock port in dev mode
│   ├── resources/
│   │   └── wiremock/
│   │       ├── __files/
│   │       │   └── blotter/             ← Vite build output (dist/)
│   │       └── mappings/
│   │           ├── inquiry-post.json
│   │           ├── inquiry-quote-post.json
│   │           ├── inquiry-unknown-isin.json  (priority stub → 404)
│   │           └── inquiries-get.json
│   └── java/
│       └── stepdefs/
│           └── BondBlotterSteps.java
└── resources/
    └── features/
        └── BondBlotter.feature
```

### Maven wiring

Add to `pom.xml` exec-maven-plugin (new execution, same plugin already present):
```xml
<execution>
  <id>build-blotter-webapp</id>
  <phase>test-compile</phase>
  <goals><goal>exec</goal></goals>
  <configuration>
    <skip>${blotter.build.skip}</skip>   <!-- default false; set true for probe-only runs -->
    <executable>npm</executable>
    <workingDirectory>${project.basedir}/src/test/webapp</workingDirectory>
    <arguments><argument>run</argument><argument>build</argument></arguments>
  </configuration>
</execution>
```

Vite `outDir` set to `../../test/resources/wiremock/__files/blotter` so the build
lands directly in WireMock's static file directory.

### Development workflow

```bash
# Build once for Maven test runs
cd src/test/webapp && npm install && npm run build

# Live iteration with HMR (Vite proxies /api to WireMock)
mvn test-compile -Dblotter.build.skip=true   # start WireMock only
cd src/test/webapp && npm run dev             # Vite on :5173, /api → WireMock
open http://localhost:5173

# Full regression run
mvn verify
```

---

## Milestone plan

### Guiding principles

1. **Vertical slices, not horizontal layers.** Every milestone delivers a runnable,
   testable increment — never "all the React components but no tests".
2. **Unhit test first.** Each milestone opens by writing the Cucumber scenario(s).
   Run them — they must fail (red).  Implement until they pass (green).  Do not advance
   until green.
3. **Regression guard is non-negotiable.** Every quality gate re-runs the full suite
   (`mvn verify`).  A new passing scenario that breaks an old one is a failed gate.
4. **Design contracts lock before code starts.** Column `col-id` names, REST URL shapes,
   and status string values are agreed in this document and treated as frozen during
   implementation.  Changing them is a major refactor, not a quick edit.
5. **One milestone at a time.** Do not start M+1 while M quality gate is not green.

---

### Design contracts (frozen — agree before writing a line of code)

These values appear in Gherkin features, Java step definitions, TypeScript types, and
WireMock stubs simultaneously.  Changing one requires changing all four.

#### AG Grid `col-id` names (stable identifiers used in probes + step defs)

| Column | `col-id` |
|---|---|
| Select checkbox | `select` |
| ISIN | `isin` |
| Description | `description` |
| Maturity | `maturity` |
| Coupon | `coupon` |
| Notional | `notional` |
| Side | `side` |
| Client | `client` |
| Status | `status` |
| TW Bid | `twBid` |
| TW Ask | `twAsk` |
| CP+ Bid | `cpBid` |
| CP+ Ask | `cpAsk` |
| CBBT Bid | `cbbBid` |
| CBBT Ask | `cbbAsk` |
| Ref Source (control) | `refSource` |
| Convention (control) | `convention` |
| Ref Side (control) | `refSide` |
| Skew Δ (control) | `skewDelta` |
| Applied Bid | `appliedBid` |
| Applied Ask | `appliedAsk` |

#### Status values (string literals in feature files and TypeScript enums)

`PENDING` · `QUOTED` · `DONE` · `MISSED`

#### REST endpoints

| Method | Path | Success |
|---|---|---|
| POST | `/api/inquiry` | 201 |
| GET | `/api/inquiries` | 200 |
| POST | `/api/inquiry/{id}/quote` | 200 |
| POST | `/api/inquiry` with unknown ISIN | 404 |

#### Price format
- Reference prices: decimal with 3dp (e.g. `99.375`)
- Skew in **Price** convention: decimal cents (e.g. `-0.25` = minus ¼ point)
- Skew in **Spread** convention: integer basis points (e.g. `+5` = +5 bp)
- Applied prices: same 3dp format as reference prices

#### Seed data (deterministic — same ISINs used in all feature files)

| # | ISIN | Description | Coupon | Maturity | Notional | Side | Client |
|---|---|---|---|---|---|---|---|
| 1 | US912828YJ02 | UST 4.25% 2034 | 4.250 | 2034-11-15 | 10,000,000 | BUY | BLACKROCK |
| 2 | XS2346573523 | EUR IG Corp 3.5% 2029 | 3.500 | 2029-03-20 | 5,000,000 | SELL | PIMCO |
| 3 | US38141GXZ20 | Goldman Sachs 5.15% 2026 | 5.150 | 2026-05-22 | 8,000,000 | BUY | VANGUARD |
| 4 | GB0031348658 | UK Gilt 1.25% 2027 | 1.250 | 2027-07-22 | 15,000,000 | SELL | FIDELITY |
| 5 | FR0014004L86 | OAT 0.75% 2028 | 0.750 | 2028-05-25 | 7,500,000 | BUY | AMUNDI |

---

### M0 — Build pipeline spine

**Goal:** `mvn verify` compiles the React app, WireMock serves it, Playwright can navigate
to the blotter URL, and all 12 existing scenarios still pass.  This is the highest-risk
milestone — get it right before writing any blotter UI.

**Deliverables:**
- `src/test/webapp/` scaffolded (Vite + React-TS, minimal `index.html` saying "PT-Blotter")
- `vite.config.ts` with `outDir: ../../test/resources/wiremock/__files/blotter`
- `pom.xml` exec-maven-plugin execution: `npm run build` in `test-compile` phase
- WireMock `mappings/blotter-static.json` stub: `GET /blotter/**` → serve from `__files/blotter/`
- New Cucumber step: `Given the PT-Blotter is open` → navigates to `{wiremockUrl}/blotter/`

**Unhit test (write first — must fail before M0 implementation):**
```gherkin
@blotter @smoke
Scenario: PT-Blotter page loads
  Given the PT-Blotter is open
  Then the page title should contain "PT-Blotter"
```

**Quality gate:**
```bash
mvn verify -Dcucumber.filter.tags="@blotter and @smoke"   # 1/1 NEW passing
mvn verify                                                 # 13/13 total (12 old + 1 new)
```

**Known risks:**
- WireMock SPA fallback: React Router needs all unknown sub-paths to return `index.html`.
  Mitigation: do NOT use React Router — single-page app, no client-side routing.
- `npm run build` path resolution on Windows (forward vs back slashes in `outDir`).
  Mitigation: use `path.resolve(__dirname, ...)` in `vite.config.ts`.
- Maven exec plugin `npm` command not found on CI.
  Mitigation: use full path or ensure Node.js is on `PATH`; document in README.

**Exit criteria:** Quality gate green. No WireMock port conflicts. Vite build artifact
visible in `src/test/resources/wiremock/__files/blotter/`.

---

### M1 — Grid structure visible

**Goal:** AG Grid renders with all expected column groups and at least one seeded row.
No ticking, no controls, no REST — just the grid skeleton and static data.

**Deliverables:**
- `BlotterGrid.tsx` with full column definition (all `col-id` values from design contracts)
- Column groups: Identity, Status, TradeWeb, CP+, CBBT, Skew Controls, Applied Prices
- 5 seeded rows (design-contract seed data, hardcoded in component state for now)
- Row checkbox selection enabled
- All reference price cells display a static placeholder (e.g. `99.000`)

**Unhit tests:**
```gherkin
@blotter @smoke
Scenario: Blotter renders expected column groups
  Given the PT-Blotter is open
  Then the grid should display column "isin"
  And the grid should display column "twBid"
  And the grid should display column "cpBid"
  And the grid should display column "cbbBid"
  And the grid should display column "appliedBid"
  And the grid should display column "status"

Scenario: Blotter loads with seeded inquiries
  Given the PT-Blotter is open
  Then the grid should have at least 5 rows
  And the row with ISIN "US912828YJ02" should have status "PENDING"
  And the row with ISIN "XS2346573523" should have status "PENDING"
```

**Quality gate:**
```bash
mvn verify -Dcucumber.filter.tags="@blotter and @smoke"   # 3/3 passing
mvn verify                                                 # 15/15 total
```

**Known risks:**
- AG Grid React `col-id` attribute only renders when `field` or `colId` is set in
  `ColDef`.  Mitigation: always set `colId` explicitly in column definitions — never
  rely on `field` name derivation.
- Column group headers have a different DOM structure (`.ag-column-header`) than data
  column headers — ensure `getHeaderCell(colId)` in `FinanceDemoPage` still works.

**Exit criteria:** All column `col-id` attributes visible in DOM, confirmed by
`assertColumnVisible(colId)` in the step def for each col in the design contract table.

---

### M2 — Ticking reference prices

**Goal:** TW, CP+, and CBBT bid/ask cells update at ~400 ms intervals with Gaussian
noise.  The AG Grid flash animation fires on each update.  Prices stay within a
realistic range for each seeded bond.

**Deliverables:**
- `PriceSimulator.ts`: `setInterval`-based update engine, one mid-price per bond,
  Gaussian noise (σ = 0.03 points), separate bid/ask spread (0.125 pts)
- Price updates applied via `gridRef.current.api.applyTransactionAsync()`
- `enableCellChangeFlash: true` on all six reference price columns
- Prices formatted to 3dp in cell renderer

**Unhit tests:**
```gherkin
@blotter @ticking
Scenario: Reference price cells update within the live feed window
  Given the PT-Blotter is open
  When I wait up to 3 seconds for the "twBid" cell in row 0 to change value
  Then the "twBid" cell in row 0 should have a numeric value

Scenario: TW Bid cell flashes on a live update
  Given the PT-Blotter is open
  When I wait for the "twBid" cell in row 0 to flash
  Then the "twBid" cell in row 0 should have received at least one tick update

Scenario: All three reference sources are ticking
  Given the PT-Blotter is open
  Then within 3 seconds the "cpBid" cell in row 0 should change value
  And within 3 seconds the "cbbBid" cell in row 0 should change value
```

**Quality gate:**
```bash
mvn verify -Dcucumber.filter.tags="@blotter and @ticking"  # 3/3 passing
mvn verify                                                  # 18/18 total
```

**Known risks:**
- Price simulation timing: `setInterval` at 400 ms means each scenario waits up to
  ~800 ms (one missed tick plus jitter).  Use 3 s timeout budget — generous but not
  flaky.  Never hard-code tick count expectations.
- `applyTransactionAsync` batches updates — AG Grid may coalesce ticks.  Use
  `waitForCellFlash` from existing `TickingCellHelper` as-is; it already handles both
  flash mechanisms.
- Seed mid-prices must be realistic for the instrument type (UST ~99, IG Corp ~101,
  Gilts ~95 etc.) so assertions on "value in range" are stable.

**Exit criteria:** `TickingCellHelper.waitForCellFlash` and `waitForCellUpdate`
work against blotter cells without any changes to the existing helper class.

---

### M3 — REST inquiry ingestion

**Goal:** A new inquiry submitted via `POST /api/inquiry` appears as a new PENDING row
in the blotter.  Unknown ISINs return 404.  `GET /api/inquiries` returns seeded data.

**Deliverables:**
- WireMock stubs: `inquiry-post.json`, `inquiry-unknown-isin.json` (priority 1 → 404),
  `inquiries-get.json`
- React: `useEffect` on mount calls `GET /api/inquiries` and merges with seed data
- React: a "Simulate ION RFQ" button calls `POST /api/inquiry` and appends the new row
- `api.ts` fetch wrapper; error surfaces in UI for 4xx responses

**Unhit tests:**
```gherkin
@blotter @api
Scenario: Inquiry submitted via API appears in blotter as PENDING
  Given the PT-Blotter is open
  When a new inquiry is submitted for ISIN "US38141GXZ20" notional "3000000" side "BUY" client "SCHRODERS"
  Then the blotter should contain a row with ISIN "US38141GXZ20"
  And that row should have status "PENDING"

Scenario: Submission returns 201 with a non-blank inquiry ID
  When a new inquiry is submitted for ISIN "GB0031348658" notional "2000000" side "SELL" client "INVESCO"
  Then the API response status should be 201
  And the response should contain a non-blank "inquiry_id"

Scenario: Unknown ISIN is rejected with 404
  When a new inquiry is submitted for ISIN "UNKNOWN-ISIN-XYZ"
  Then the API response status should be 404
```

**Quality gate:**
```bash
mvn verify -Dcucumber.filter.tags="@blotter and @api"  # 3/3 passing
mvn verify                                              # 21/21 total
```

**Known risks:**
- WireMock stub matching: the unknown-ISIN stub must use priority 1 and match on the
  request body `isin` field.  Same pattern as the existing `UNKNOWN_TRADER` stub in
  `MockBlotterServer` — copy that pattern.
- React `fetch` to WireMock uses a relative URL (`/api/inquiry`).  WireMock must serve
  the React app AND handle the API call on the same port — which it does since both come
  from the same `MockBlotterServer` instance.
- `GET /api/inquiries` response must be valid JSON array; WireMock stub body must not
  be empty.

**Exit criteria:** `PortfolioSteps` patterns reused wholesale in `BondBlotterSteps`
for the REST assertions — no new REST infrastructure invented.

---

### M4 — Skew controls and APPLY

**Goal:** Trader selects a row, sets ref source / convention / ref side / skew amount
in the toolbar, presses APPLY, and the Applied Bid or Applied Ask column updates with
the mathematically correct skewed price.

**Deliverables:**
- Toolbar components: Ref Source select (`TW` / `CP+` / `CBBT`), Convention select
  (`Price` / `Spread`), Ref Side select (`Bid` / `Ask`), Skew Δ number input, APPLY button
- APPLY logic:
  - Price convention: `applied = refPrice ± skewDelta` (decimal points)
  - Spread convention: `applied = refPrice` at `refSpread ± skewDeltaBp` (basis points)
- Applied Bid and Applied Ask columns populated on row after APPLY

**Unhit tests:**
```gherkin
@blotter @workflow
Scenario: Applying a price skew to a single row updates applied prices
  Given the PT-Blotter is open
  When I select the row with ISIN "US912828YJ02"
  And I set the ref source to "TW", convention "Price", ref side "Bid", skew "-0.25"
  And I press APPLY
  Then the applied bid for ISIN "US912828YJ02" should equal the TW Bid minus 0.25

Scenario: Applying a spread skew uses basis points
  Given the PT-Blotter is open
  When I select the row with ISIN "XS2346573523"
  And I set the ref source to "CP+", convention "Spread", ref side "Ask", skew "+5"
  And I press APPLY
  Then the applied ask spread for ISIN "XS2346573523" should equal the CP+ Ask spread plus 5bp

Scenario: APPLY button is disabled when no row is selected
  Given the PT-Blotter is open
  Then the APPLY button should be disabled
```

**Quality gate:**
```bash
mvn verify -Dcucumber.filter.tags="@blotter and @workflow"  # 3/3 passing
mvn verify                                                   # 24/24 total
```

**Known risks:**
- Floating-point precision in `applied = refPrice - skewDelta`.  Mitigation: use
  `NumericComparator.assertEquivalent()` (already exists) rather than exact string
  equality; assert `Math.abs(actual - expected) < 0.001`.
- Reference price is ticking while trader is setting skew controls — race condition in
  the assertion.  Mitigation: step def reads the reference price at the moment APPLY is
  pressed (from the DOM via probe), then computes expected applied price from that snapshot.
- Toolbar state resets on deselect — ensure controls persist their values during the test.

**Exit criteria:** `NumericComparator` validates applied prices.  No `Thread.sleep` in
step defs.  `TickingCellHelper` or `GridHarness` reused unmodified.

---

### M5 — SEND and status machine

**Goal:** SEND fires `POST /api/inquiry/{id}/quote`, row moves to QUOTED status,
skew controls and SEND button are disabled for that row.  A QUOTED row cannot be
re-sent.

**Deliverables:**
- SEND button in toolbar (enabled only when ≥1 selected row has applied prices)
- On click: calls `POST /api/inquiry/{id}/quote` for each selected row
- On 200 response: row `status` field set to `QUOTED` in grid state
- QUOTED rows: skew control cells render as read-only/disabled; checkbox still selectable
  (for future DONE/MISSED transitions)
- WireMock stub: `inquiry-quote-post.json` → `200 { inquiry_id, status: "QUOTED" }`

**Unhit tests:**
```gherkin
@blotter @workflow
Scenario: After SEND the row status becomes QUOTED
  Given the PT-Blotter is open
  And I have applied a skew to the row with ISIN "US912828YJ02"
  When I select the row with ISIN "US912828YJ02"
  And I press SEND
  Then the row with ISIN "US912828YJ02" should have status "QUOTED"

Scenario: Skew controls are disabled for a QUOTED row
  Given the row with ISIN "US912828YJ02" has been sent and is QUOTED
  Then the skew controls for that row should be read-only
  And the SEND button should be disabled when only QUOTED rows are selected

Scenario: SEND calls the quote API with applied prices
  Given I have applied "TW Bid -0.25" to the row with ISIN "US912828YJ02"
  When I press SEND
  Then the API should have received a quote request for "US912828YJ02"
  And the request body should contain the applied bid price
```

**Quality gate:**
```bash
mvn verify -Dcucumber.filter.tags="@blotter and @workflow"  # 6/6 passing
mvn verify                                                   # 27/27 total
```

**Known risks:**
- WireMock stub for `/api/inquiry/{id}/quote` must match any `{id}` path parameter.
  Use WireMock URL regex pattern: `urlPathMatching("/api/inquiry/.*/quote")`.
- Disabling AG Grid cell components (dropdowns, inputs) in QUOTED rows: use
  `cellStyle` / `cellClassRules` to add a `disabled` class, and `suppressKeyboardEvent`
  / `editable: false` per-cell based on row data.  Test via `aria-disabled` attribute
  assertion in step def.

**Exit criteria:** WireMock verifies the `/quote` stub was called with correct body.
`GridHarness.getSiblingCellText` reused to read applied price from the SEND request body.

---

### M6 — Multi-row APPLY and SEND

**Goal:** Trader selects 2+ rows and applies the same skew parameters to all selected
rows in one APPLY press.  SEND sends all selected rows simultaneously.

**Deliverables:**
- APPLY iterates over all selected rows, computing applied price from each row's own
  reference price at the time of the APPLY press
- SEND iterates, calling `POST /api/inquiry/{id}/quote` for each selected PENDING row
  (skips already-QUOTED rows)
- Selection counter badge in toolbar ("2 rows selected")

**Unhit tests:**
```gherkin
@blotter @workflow @multi
Scenario: Applying skew to two selected rows updates both applied prices
  Given the PT-Blotter is open
  When I select rows with ISINs "US912828YJ02" and "XS2346573523"
  And I set the ref source to "TW", convention "Price", ref side "Bid", skew "-0.125"
  And I press APPLY
  Then the applied bid for "US912828YJ02" should equal its TW Bid minus 0.125
  And the applied bid for "XS2346573523" should equal its TW Bid minus 0.125

Scenario: Sending two rows at once quotes both
  Given I have applied a skew to rows "US912828YJ02" and "XS2346573523"
  When I select both rows and press SEND
  Then the row "US912828YJ02" should have status "QUOTED"
  And the row "XS2346573523" should have status "QUOTED"

Scenario: SEND skips already-QUOTED rows in a mixed selection
  Given row "US912828YJ02" is already QUOTED
  And row "XS2346573523" is PENDING with applied prices
  When I select both rows and press SEND
  Then row "XS2346573523" should become QUOTED
  And the API should have been called exactly once for the quote endpoint
```

**Quality gate:**
```bash
mvn verify -Dcucumber.filter.tags="@blotter"  # all blotter scenarios passing
mvn verify                                     # 30/30 total
```

**Known risks:**
- AG Grid `getSelectedRows()` returns a snapshot; reference prices may tick between
  the APPLY call and the assertion.  Mitigation: step def stores the reference price
  read from the DOM before pressing APPLY, uses that as the expected value basis.
- WireMock call count verification: use `WireMock.verify(1, postRequestedFor(urlPathMatching(...)))`
  in the step def for the "called exactly once" assertion.

**Exit criteria:** WireMock call count assertions pass.  All three multi-row scenarios
pass reliably over 5 consecutive `mvn verify` runs (no flakiness from ticking prices).

---

### M7 — DSL crystallisation

**Goal:** The step definition layer reads like a domain language, not like test
automation plumbing.  A new scenario can be written using only existing step phrases —
no Java code needed.  `BondBlotterSteps.java` contains zero direct Playwright calls.

**Deliverables:**
- `BlotterDsl.java` — encapsulates all Playwright interaction with the blotter:
  - `openBlotter()`, `selectRowByIsin(String)`, `selectRowsByIsins(String...)`
  - `setSkewParameters(refSource, convention, refSide, skewDelta)`
  - `pressApply()`, `pressSend()`
  - `getRowStatus(isin)`, `getAppliedBid(isin)`, `isSkewControlDisabled(isin)`
  - `submitInquiryViaApi(isin, notional, side, client)` → uses `page.request()`
- `BondBlotterSteps.java` delegates 100% to `BlotterDsl`
- `BondBlotterSteps.java` has no `import com.microsoft.playwright.Locator` or `Page` usages

**Unhit test (a new scenario using only pre-existing step phrases — no new Java needed):**
```gherkin
@blotter @dsl
Scenario: Full inquiry-to-quote workflow in DSL steps
  Given the PT-Blotter is open
  And a new inquiry is submitted for ISIN "FR0014004L86" notional "4000000" side "BUY" client "AXA IM"
  When I select the row with ISIN "FR0014004L86"
  And I set the ref source to "CBBT", convention "Price", ref side "Ask", skew "+0.125"
  And I press APPLY
  And I press SEND
  Then the row with ISIN "FR0014004L86" should have status "QUOTED"
  And the skew controls for that row should be read-only
```

**Quality gate:**
```bash
mvn verify -Dcucumber.filter.tags="@blotter and @dsl"   # 1/1 new scenario, no new Java
mvn verify                                               # 31/31 total
```

Code review gate (manual, before declaring M7 done):
- `BondBlotterSteps.java`: zero `page.locator()`, zero `page.evaluate()`, zero `Page` imports
- `BlotterDsl.java`: all probes called through `window.agGridProbes.*` (no inline JS)
- Any new blotter scenario expressible using existing step phrases alone

**Exit criteria:** Pair-review the feature file with someone unfamiliar with the codebase.
If they can understand what the test does without reading any Java, the DSL is working.

---

### M8 — Full regression suite and evidence

**Goal:** All 31+ scenarios run in CI under a single `mvn verify`.  Blotter scenarios
are correctly tagged for selective execution.  HTML regression report includes the
blotter feature.

**Deliverables:**
- Tag taxonomy finalised: `@blotter`, `@blotter-smoke`, `@ticking`, `@api`,
  `@workflow`, `@multi`, `@dsl`
- `cucumber.properties` updated if needed
- CI instructions updated in README (Node.js prerequisite, `npm install` step)
- `mvn verify` report includes blotter scenarios with pass/fail charts

**Quality gate:**
```bash
# Full suite — the only gate that matters for CI
mvn verify   # all scenarios passing, HTML report generated

# Selective tag examples that must all work
mvn verify -Dcucumber.filter.tags="@blotter and not @ticking"   # fast, no live prices
mvn verify -Dcucumber.filter.tags="@blotter and @smoke"         # smoke only
mvn verify -Dcucumber.filter.tags="@ticking"                    # all ticking (finance + blotter)
mvn verify -Dcucumber.filter.tags="@blotter and @api"           # API-only, no browser needed
```

**Exit criteria:**
- 5 consecutive clean `mvn verify` runs on a fresh checkout (validates no environment
  bleed between scenarios)
- README documents Node.js prerequisite and blotter build step
- HTML report at `target/cucumber-html-reports/overview-features.html` shows blotter
  feature with correct scenario count and pass indicators

---

### Milestone summary

| Milestone | Core deliverable | New scenarios | Cumulative total | Key risk |
|---|---|---|---|---|
| M0 | Build pipeline + WireMock serving | 1 smoke | 13 | Vite outDir + WireMock SPA routing |
| M1 | Grid columns + static seed data | 2 smoke/data | 15 | AG Grid `col-id` not rendering |
| M2 | Ticking reference prices | 3 ticking | 18 | Flash timing / test flakiness |
| M3 | REST inquiry ingestion | 3 api | 21 | WireMock stub matching + ISIN 404 |
| M4 | Skew controls + APPLY | 3 workflow | 24 | Float precision in applied price |
| M5 | SEND + status machine | 3 workflow | 27 | Row locking + WireMock call verify |
| M6 | Multi-row APPLY/SEND | 3 multi | 30 | Ticking race + WireMock call count |
| M7 | DSL crystallisation | 1 dsl | 31 | Zero raw Playwright in step defs |
| M8 | Full regression + evidence | — | 31 | Tag taxonomy + CI Node.js prereq |
