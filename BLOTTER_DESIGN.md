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

## Recommendation

### Phase 1 (now): Approach 1 — Vanilla AG Grid + WireMock

Build `blotter.html` served from WireMock static files.  The goal at this stage is
a **working test target**, not a beautiful UI.  Approach 1 delivers:

- Running in one `mvn verify` with no new prerequisites
- Identical AG Grid DOM structure → every existing probe, locator, and harness works today
- Deterministic mock data → reliable CI
- Full bond trading workflow (Inquiry → Skew → Apply → Send → Quoted)

The page is ~600–800 lines of vanilla JS/HTML which is entirely manageable for a mock.

### Phase 2 (later): Migrate to Approach 2 — React + Vite

Once the workflow and test DSL are stable, extract the blotter into a proper React app.
The migration is low-risk because:
- AG Grid DOM structure is identical — no Playwright/probe changes needed
- Cucumber features remain unchanged
- Only the page source changes (from vanilla JS to React components)

Approach 2 is the right long-term home because complex UI state (dropdown-driven skew
controls, row-level status badges, multi-select APPLY/SEND logic) is genuinely simpler
to build and maintain in React than vanilla JS.

### Approach 3: Do not use

VUU is excellent engineering, but it is a full trading platform, not a test target.
The grid is not AG Grid (breaking all our probe work) and the Scala backend is a
disproportionate infrastructure commitment for a mock.

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

## Next steps (once approach agreed)

1. Create `src/test/resources/wiremock/__files/blotter.html` (vanilla AG Grid, ~700 lines)
2. Add WireMock stub JSON mappings for `/api/inquiry` endpoints
3. Extend `MockBlotterServer` to serve the static HTML
4. Write `BondBlotter.feature` with the core workflow scenarios
5. Implement `BondBlotterSteps.java` using existing `GridHarness` + new DSL helpers
6. Evolve probe bundle with any blotter-specific probes needed
