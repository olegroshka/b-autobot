# PT-Blotter Design

Fixed income bond portfolio trading blotter for the b-autobot BDD regression suite.
Built with **React 18 + AG Grid + Vite**, served by an embedded WireMock server, with
a companion **Config Service** that gates access to privileged actions via `isPTAdmin`.

---

## Trading Workflow

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
  Uses toolbar:
    • Source   — TW / CP+ / CBBT
    • Side     — Bid / Ask / Mid
    • Markup   — [−][  value  ][+]  (numeric; step = 1 for c, 1 for bp)
    • Units    — c (price points)  or  bp (basis points)
        │
        ▼
  Presses APPLY  ──►  Price / Spread columns recalculated on row
        │
        ▼
  Presses SEND   ──►  Row status → QUOTED, Sent Price / Sent Spread snapshot taken
        │
        │  Row stays fully editable — re-APPLY → re-SEND refreshes the snapshot.
        │
        ▼  (admin only — isPTAdmin = true, fetched from Config Service on startup)
  Presses RELEASE PT ──►  Row status → RELEASED
```

---

## Column Layout

| Column group    | Columns                                                                         |
|-----------------|---------------------------------------------------------------------------------|
| Identity        | Checkbox, Portfolio ID, PT Line ID, ISIN, Description, Maturity, Coupon, Notional, Side, Client |
| Status          | Status (PENDING / QUOTED / DONE / MISSED / RELEASED)                           |
| TradeWeb (TW)   | TW Price *(ticking, "bid / ask")*, TW Spread *(ticking, "bid / ask")*          |
| Bloomberg CP+   | CP+ Price *(ticking, "bid / ask")*, CP+ Spread *(ticking, "bid / ask")*        |
| Bloomberg CBBT  | CBBT Price *(ticking, "bid / ask")*, CBBT Spread *(ticking, "bid / ask")*      |
| Applied values  | Pricing Action, Price, Spread                                                   |
| Sent snapshot   | Sent Price, Sent Spread                                                         |

---

## Stack

| Component        | Technology                                                  |
|------------------|-------------------------------------------------------------|
| Blotter frontend | React 18 + AG Grid React (`ag-grid-react`) + Vite           |
| Price simulation | `setInterval` with Gaussian noise at ~400 ms               |
| REST mock        | WireMock (embedded, dynamic port)                           |
| Config Service   | JDK `com.sun.net.httpserver.HttpServer` (in-memory store)  |
| Config UI        | React 18 + Vite (served by MockConfigServer at /config-service/) |
| Deployment Server | JDK `com.sun.net.httpserver.HttpServer` (12 seed services) |
| Deployment UI    | React 18 + AG Grid + Vite (served by MockDeploymentServer at /deployment/) |

---

## Directory Layout

```
src/test/
├── java/
│   ├── stepdefs/
│   │   ├── BondBlotterSteps.java    # Delegates 100% to BlotterDsl
│   │   ├── ConfigServiceSteps.java  # Config Service step defs (Playwright-free)
│   │   └── Hooks.java               # @BeforeAll/@AfterAll — starts MockBlotterServer + MockConfigServer
│   └── utils/
│       ├── BlotterDsl.java          # All Playwright interactions for the blotter
│       ├── BlotterDevServer.java    # Standalone launcher for manual exploration (port 9099)
│       ├── ConfigServiceDsl.java    # JDK HttpClient wrapper for Config Service REST API
│       ├── ConfigDevServer.java     # Standalone launcher for Config Service + UI (port 8090)
│       ├── DeploymentDsl.java       # Deployment Dashboard DSL — API + browser
│       ├── DeploymentDevServer.java # Standalone launcher for Deployment Dashboard (port 9098)
│       ├── MockBlotterServer.java   # Embedded WireMock + PT-Blotter REST stubs
│       ├── MockConfigServer.java    # Config microservice (JDK HttpServer, in-memory)
│       └── MockDeploymentServer.java # Deployment registry (JDK HttpServer, 12 services)
├── resources/
│   ├── features/
│   │   ├── BondBlotter.feature     # M0–M8 + precondition blotter scenarios (39 scenarios)
│   │   ├── ConfigService.feature   # Config Service REST API scenarios (14 scenarios)
│   │   └── Deployment.feature      # Deployment Dashboard API + grid + filter (15 scenarios)
│   ├── config-service-ui/          # Vite build output for Config UI (git-committed)
│   ├── deployment-ui/              # Vite build output for Deployment Dashboard (git-committed)
│   └── wiremock/__files/blotter/   # Vite build output for PT-Blotter (git-committed)
├── webapp/                          # PT-Blotter React app source
│   └── src/
│       ├── App.tsx                  # Main grid + APPLY / SEND / RELEASE PT handlers
│       ├── Toolbar.tsx              # Toolbar: Source / Side / Markup / Units / APPLY / SEND / RELEASE PT
│       ├── PriceSimulator.ts        # Gaussian-noise ticking at ~400 ms
│       ├── filterUtils.ts           # Column-specific filter parsing ("Portfolio:..." etc.)
│       ├── api.ts                   # releasePt(), postQuote(), fetchInquiries()
│       └── types.ts                 # Inquiry, Status (PENDING/QUOTED/DONE/MISSED/RELEASED), AppliedConfig
├── webapp-config/                   # Config Service React app source
│   └── src/
│       ├── App.tsx                  # Namespace panel + type dropdown + config entry CRUD
│       ├── api.ts                   # fetchNamespaces, fetchTypes, fetchConfig, saveConfig, deleteConfig
│       └── types.ts                 # ConfigValue = string | number | boolean
└── webapp-deployment/               # Deployment Dashboard React app source
    └── src/
        ├── App.tsx                  # AG Grid dark theme, StatusCell renderer, 10 columns, filter
        ├── api.ts                   # fetchDeployments() — reads ?apiUrl= or same-origin
        └── types.ts                 # ServiceStatus, Deployment interface
```

---

## Config Service

### REST API

| Method   | Path                              | Description                          |
|----------|-----------------------------------|--------------------------------------|
| `GET`    | `/api/config`                     | List all namespaces (JSON array)     |
| `GET`    | `/api/config/{ns}`                | List types under namespace           |
| `GET`    | `/api/config/{ns}/{type}`         | All keys + values under type         |
| `GET`    | `/api/config/{ns}/{type}/{key}`   | Single entry (JSON object)           |
| `PUT`    | `/api/config/{ns}/{type}/{key}`   | Create or update entry               |
| `DELETE` | `/api/config/{ns}/{type}/{key}`   | Remove entry                         |

Static files: `GET /config-service/**` → served from `src/test/resources/config-service-ui/`

### Seed Data

| Namespace          | Type          | Key       | Values                                              |
|--------------------|---------------|-----------|-----------------------------------------------------|
| `credit.pt.access` | `Permissions` | `doej`    | `{"isPTAdmin": false}`                              |
| `credit.pt.access` | `Permissions` | `smithj`  | `{"isPTAdmin": true}`                               |
| `credit.pt.access` | `Permissions` | `patelv`  | `{"isPTAdmin": false}`                              |
| `credit.pt.access` | `Permissions` | `nguyenl` | `{"isPTAdmin": true}`                               |
| `credit.booking`   | `Settings`    | `default` | `{"autoBook": false, "bookingDesk": "FIXED_INCOME"}`|
| `credit.risk`      | `Limits`      | `default` | `{"maxNotional": 50000000, "alertThreshold": 0.9}` |
| `market.data`      | `Sources`     | `default` | `{"primary": "TW", "fallback": "CP+"}`              |

### Blotter Integration

The blotter fetches `isPTAdmin` on startup using URL parameters:

```
http://localhost:{wiremockPort}/blotter/?user=smithj&configUrl=http://localhost:{configPort}
```

If `isPTAdmin` is `true`, the **RELEASE PT** button is enabled.
Tests pass `?user=` and `?configUrl=` via `BlotterDsl.openBlotter(String user)`.

---

## APPLY Formula

```
price  = refCell[bid|ask|mid] + markup    (units = c — price points)
spread = refCell[bid|ask|mid] + markup    (units = bp — basis points)
```

`refCell` is the bid or ask side of the combined `"bid / ask"` string for the selected
reference source, or the arithmetic mean for Mid.

After APPLY, `appliedConfig` is stored on the row. The price simulator re-derives
`price`/`spread` on every 400 ms tick — the computed value continuously tracks live
reference prices until the row reaches DONE / MISSED (not cleared by SEND).

---

## Milestone Status

| Milestone | Goal                                        | Scenarios | Status |
|-----------|---------------------------------------------|-----------|--------|
| M0        | Page loads                                  | 1         | ✓ Done |
| M1        | Grid columns + seed rows                    | 2         | ✓ Done |
| M2        | Ticking TW / CP+ / CBBT prices             | 3         | ✓ Done |
| M3        | REST inquiry API                            | 2         | ✓ Done |
| M4        | Toolbar APPLY (price + spread)              | 6         | ✓ Done |
| M5        | SEND → QUOTED + sentPrice snapshot          | 3         | ✓ Done |
| M6        | Multi-row APPLY / SEND                      | 3         | ✓ Done |
| M7        | End-to-end DSL re-quote workflow            | 1         | ✓ Done |
| M8        | RELEASE PT access control + workflow        | 5         | ✓ Done |
| Config    | Config Service REST API + CRUD              | 14        | ✓ Done |
| Deploy    | Deployment Dashboard API + grid + filter    | 15        | ✓ Done |
| Gate      | @precondition version-gate scenario         | 1         | ✓ Done |

**Current regression total: 66 / 66 passing**

```bash
# Full suite with Vite rebuild
mvn verify -Dblotter.build.skip=false   # → 66/66

# Config Service only (no build needed)
mvn verify -Dcucumber.filter.tags="@config-service"   # → 14/14

# Deployment Dashboard only
mvn verify -Dcucumber.filter.tags="@deployment"   # → 15/15

# Version gate precondition only
mvn verify -Dcucumber.filter.tags="@precondition"   # → 1/1

# M8 access control + workflow (needs Vite build)
mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m8"   # → 5/5
```

---

## Key Implementation Decisions

### `suppressColumnVirtualisation={true}`

AG Grid virtualises columns by default, removing off-screen column DOM nodes.
Setting this to `true` keeps all columns in the DOM at all times so Playwright
locators like `[col-id='sentPrice']` always resolve.

### RELEASE PT — permission-only gate

```tsx
disabled={!isReleasePtEnabled}
```

**Not** `disabled={selectedCount === 0 || !isReleasePtEnabled}`.
The access-control test for `doej` asserts the button is disabled without selecting
any rows. A selection gate would make the test ambiguous (permission vs selection).
Clicking with zero selected rows is a no-op — the for-loop doesn't execute.

### Config Service — zero new Maven dependencies

Uses `com.sun.net.httpserver.HttpServer` (JDK built-in, Java 6+).
No Jetty, no Spring, no embedded container — just a standard library class.

### Vite fixed asset names

```ts
// vite.config.ts
entryFileNames: 'assets/index.js',
chunkFileNames: 'assets/[name].js',
assetFileNames: 'assets/[name].[ext]',
```

WireMock stubs the JS/CSS by exact filename. Content-hash suffixes would break
the stubs on every rebuild, so hashing is disabled.

### `blotter.build.skip=true` default

Vite assets are committed to git (`src/test/resources/wiremock/__files/blotter/`).
Tests run against the committed build by default — no Node.js required in CI.
Pass `-Dblotter.build.skip=false` to force a rebuild (e.g., after frontend changes).
Same pattern applies to `-Dconfig.build.skip=false` for the Config Service UI.

---

## Running Interactively

### PT-Blotter

```bash
# 1. Build once
mvn test-compile -Dblotter.build.skip=false

# 2. Start WireMock mock server (port 9099)
mvn exec:java -Dexec.mainClass=utils.BlotterDevServer -Dexec.classpathScope=test

# 3a. Open pre-built page
open http://localhost:9099/blotter/

# 3b. Or use Vite dev server (live HMR)
cd src/test/webapp
VITE_WIREMOCK_PORT=9099 npm run dev
# → http://localhost:5173/blotter/
```

### Config Service UI

```bash
# 1. Build once
mvn test-compile -Dconfig.build.skip=false

# 2. Start Config Service backend (port 8090)
mvn exec:java -Dexec.mainClass=utils.ConfigDevServer -Dexec.classpathScope=test

# 3a. Open pre-built page
open http://localhost:8090/config-service/

# 3b. Or use Vite dev server (live HMR)
cd src/test/webapp-config
VITE_CONFIG_PORT=8090 npm run dev
# → http://localhost:5174/config-service/
```
