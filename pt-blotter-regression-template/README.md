# pt-blotter-regression-template

A **copy-and-adapt** starting point for building a real-system BDD regression suite on top of [`b-bot-core`](../b-bot-core).

> **This is a template, not a library.**
> Copy this directory into your own project, rename it, and adapt it.
> The PT-Blotter wiring is a working example — it runs against live mock servers
> and demonstrates the full architecture you'd use against a real UAT environment.

---

## What the demo actually does

Running `mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat` drives
**15 runnable scenarios** across three mock servers that simulate a fixed-income trading platform:

| Mock server | Port | What it simulates |
|-------------|------|-------------------|
| `BlotterDevServer` | 9099 | PT-Blotter React UI + WireMock REST API |
| `ConfigDevServer` | 8090 | Config Service — permissions microservice |
| `DeploymentDevServer` | 9098 | Deployment Dashboard — service registry |

The scenarios demonstrate eight observable aspects of the system:

| # | Tag | What is proved |
|---|-----|----------------|
| 1 | `@precondition` | Mock UAT stack is live; services are at the tested versions |
| 2 | `@smoke` | PT-Blotter UI loads; AG Grid renders |
| 3 | `@grid` | Column schema is intact; seed data is in PENDING status |
| 4 | `@ticking` | Live price simulator is ticking at ~400 ms |
| 5 | `@workflow` | APPLY → SEND pricing workflow; price/spread captured in sentPrice |
| 6 | `@access` | RELEASE PT respects isPTAdmin from the Config Service |
| 7 | `@config-service` | Permission namespace present; doej=false, smithj=true |
| 8 | `@deployment` | Service registry lists all critical services at correct versions |

---

## What you get out of the box

```
pt-blotter-regression-template/
├── src/test/java/
│   ├── descriptors/
│   │   ├── BlotterDescriptor.java        ← AppDescriptor: name, DSL factory, health path
│   │   ├── ConfigServiceDescriptor.java  ← REST-only descriptor for config service
│   │   └── DeploymentDescriptor.java     ← Hybrid descriptor for deployment dashboard
│   ├── utils/
│   │   ├── PtBlotterDsl.java             ← All Playwright calls: nav, grid, toolbar, assertions
│   │   ├── ConfigServiceDsl.java         ← JDK HttpClient REST calls to config API
│   │   └── DeploymentDsl.java            ← JDK HttpClient REST calls to deployment API
│   └── stepdefs/
│       ├── Hooks.java                    ← @BeforeAll/@AfterAll wiring (5 lines of bootstrap)
│       ├── AppPreconditionSteps.java     ← Generic health check steps (ready to use as-is)
│       ├── BlotterSteps.java             ← Blotter step definitions (100% delegates to DSL)
│       ├── ConfigServiceSteps.java       ← Config Service step definitions
│       └── DeploymentSteps.java          ← Deployment step definitions
└── src/test/resources/
    ├── features/
    │   └── PtBlotterRegression.feature   ← 15 runnable scenarios
    ├── application.conf                  ← Base config + full commented override reference
    ├── application-devserver.conf        ← Blotter-only smoke (port 9099)
    ├── application-mockuat.conf          ← All three mock servers (ports 9099/8090/9098)
    └── cucumber.properties
```

---

## Running the demo

### Step 1 — Start the mock UAT environment

**Unix/Mac:**
```bash
scripts/start-mock-uat.sh
```

**Windows:**
```batch
scripts\start-mock-uat.bat
```

The script starts all three servers in the background and waits for them to be ready
(blotter at port 9099, config service at 8090, deployment at 9098).

### Step 2 — Run the full suite

```bash
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat
# → 15/15 scenarios pass
```

### Step 3 — Stop the environment

```bash
scripts/stop-mock-uat.sh    # Unix/Mac
scripts\stop-mock-uat.bat   # Windows
```

---

## Running subsets by tag

```bash
# Health gate only (fastest — pure REST, no browser)
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat \
    -Dcucumber.filter.tags="@precondition"

# Smoke (1 browser scenario)
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat \
    -Dcucumber.filter.tags="@smoke"

# Trading workflow (4 browser scenarios)
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat \
    -Dcucumber.filter.tags="@workflow"

# Config service REST checks (3 scenarios, no browser)
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat \
    -Dcucumber.filter.tags="@config-service"

# Deployment registry checks (2 scenarios, no browser)
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat \
    -Dcucumber.filter.tags="@deployment"

# Everything except the slow ticking scenario
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat \
    -Dcucumber.filter.tags="not @ticking"
```

---

## Headed browser (watch it run)

```bash
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat \
    -Db-bot.browser.headless=false
```

---

## Adapting for your real system

### 1. Copy the module

```bash
cp -r pt-blotter-regression-template my-app-regression
```

### 2. Add your app descriptor

Edit `descriptors/BlotterDescriptor.java`:
- Change `name()` to match your app's key in config (e.g. `"my-trading-app"`)
- Change the DSL type and factory
- Set `healthCheckPath()` to a real liveness endpoint

### 3. Build your DSL

Edit `utils/PtBlotterDsl.java`:
- Replace navigation methods with your app's URLs
- Add methods for every observable user action and assertion your scenarios need
- Use `ctx.getWebUrl()`, `ctx.getApiBaseUrl()`, `ctx.getUser(role)` — never hardcode

### 4. Write your environment config

Create `src/test/resources/application-uat.conf`:
```hocon
b-bot.apps.my-trading-app {
  webUrl  = "https://uat-blotter.firm.com/"
  apiBase = "https://uat-api.firm.com"
  users   { trader = jsmith, admin = aadmin }
}
```

### 5. Run against UAT

```bash
mvn verify -pl my-app-regression -Db-bot.env=uat
```

---

## All configurable settings

Override any `b-bot-core` default in your env conf or on the CLI:

```hocon
b-bot {
  browser {
    type     = chromium          # chromium | firefox | webkit
    headless = false             # open a real browser window
    viewport { width = 1920, height = 1080 }
  }
  timeouts {
    navigation     = 30s         # page.navigate() budget
    cellFlash      = 3s          # wait for ag-cell-data-changed class
    gridRender     = 10s         # wait for AG Grid to show first row
    apiResponse    = 10s         # REST call timeout
    gridFastPath   = 500ms       # phase-1 DOM scan timeout in GridHarness
    gridRowInDom   = 5s          # phase-2 row confirm timeout
    gridHasRows    = 5s          # phase-3 initial wait
    gridScrollStep = 2s          # phase-3 per-step scroll wait
    healthCheck    = 10s         # BBotRegistry health probe timeout
  }
  grid {
    renderPollMs   = 100         # waitForFunction poll interval (ms)
    maxScrollSteps = 200         # max scroll iterations in GridHarness
  }
  ticking {
    pollMs = 150                 # ticking cell poll interval (ms)
  }
}
```

---

## Troubleshooting

**Scenarios fail with "Could not open PT-Blotter" or "Health check failed"**

The mock UAT environment is not running. Start it:
```bash
scripts/start-mock-uat.sh     # Unix/Mac
scripts\start-mock-uat.bat    # Windows
```

**Port already in use**

Stop any existing server instances:
```bash
scripts/stop-mock-uat.sh
```

Or find and kill the process manually:
```bash
lsof -i :9099    # Unix/Mac
netstat -ano | findstr "9099"   # Windows
```

**Tests still skipped after adding -Db-bot.env=mockuat**

The `real-env` Maven profile activates automatically when `-Db-bot.env` is set.
Make sure you are running against the `pt-blotter-regression-template` module:
```bash
mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat
```
