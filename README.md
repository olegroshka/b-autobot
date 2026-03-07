# b-autobot

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Playwright](https://img.shields.io/badge/Playwright-1.49.0-green.svg)](https://playwright.dev/java/)
[![Cucumber](https://img.shields.io/badge/Cucumber-7.18.1-brightgreen.svg)](https://cucumber.io/)

> **Playwright + Cucumber BDD test automation framework for financial trading UIs.**
> Enterprise-grade browser automation against AG Grid React applications, with a
> publishable core library and a copy-adapt template for real-system UAT consumers.

---

## At a glance

Feature files read like business requirements. Here is a complete end-to-end test
that submits a bond portfolio, prices it on a live ticking grid, and confirms the
QUOTED status — with **no hardcoded URLs, ISINs, or user names** anywhere in the
scenario:

```gherkin
@showcase
Scenario: Full credit portfolio lifecycle — REST submission through to QUOTED
  # 1. REST: POST both portfolio bonds through the blotter inquiry API
  Given I submit all inquiries for portfolio "HYPT_1"

  # 2. Grid: open the blotter — both bonds are visible as PENDING inquiries
  And the PT-Blotter is open
  Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "PENDING"
  And the row with ISIN from "HYPT_1" field "ISIN2" should have status "PENDING"

  # 3. Pricing: select both bonds, apply TradeWeb Mid reference price
  When I select the row with ISIN from "HYPT_1" field "ISIN1"
  And I select the row with ISIN from "HYPT_1" field "ISIN2"
  And I set the toolbar source "TW" side "Mid" markup "0" units "c"
  And I press APPLY
  Then the "price" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value
  And the "price" for ISIN from "HYPT_1" field "ISIN2" should be a numeric value

  # 4. Quote: SEND — live prices are snapshotted; both inquiries move to QUOTED
  When I press SEND
  Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "QUOTED"
  And the row with ISIN from "HYPT_1" field "ISIN2" should have status "QUOTED"
  And the "sentPrice" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value
  And the "sentPrice" for ISIN from "HYPT_1" field "ISIN2" should be a numeric value
```

All data — ISINs, API paths, template bodies, service versions — is declared once
in `application-mockuat.conf`. When a bond matures or an endpoint changes, update
the config; the feature files stay stable.

```gherkin
# Config service integration — role-based access control in two lines:
@access
Scenario: Trader cannot access the RELEASE PT button
  Given the PT-Blotter is open as the trader
  Then the RELEASE PT button should be disabled

@config-service
Scenario: Admin has PT admin access
  Then the user from role "admin" should have isPTAdmin "true" in config service
```

---

## Repository layout

```
b-autobot/
├── b-bot-core/                     ← Publishable library (no Cucumber dependency)
│   └── src/main/java/com/bbot/core/
│       ├── PlaywrightManager       # Thread-local Playwright lifecycle
│       ├── GridHarness             # 3-phase virtualisation-safe row finder
│       ├── TickingCellHelper       # Live-ticking cell wait/assert helpers
│       ├── ProbesLoader            # Injects window.agGridProbes bundle
│       ├── NumericComparator       # UI vs API value comparison (BigDecimal)
│       ├── config/BBotConfig       # HOCON layered config (5-layer loading)
│       └── registry/               # AppDescriptor / AppContext / BBotRegistry
│
├── b-bot-sandbox/                  ← Demo & regression suite (all 66 scenarios)
│   └── src/test/
│       ├── java/
│       │   ├── descriptors/        # BlotterAppDescriptor, ConfigServiceDescriptor, DeploymentDescriptor
│       │   ├── model/              # Jackson POJOs (Trade, TradePortfolio)
│       │   ├── pages/              # FinanceDemoPage (AG Grid Finance Demo POM)
│       │   ├── runners/            # JUnit 5 @Suite runner
│       │   ├── stepdefs/           # BondBlotterSteps, ConfigServiceSteps, DeploymentSteps, …
│       │   └── utils/              # BlotterDsl, ConfigServiceDsl, DeploymentDsl,
│       │                           # MockBlotterServer, MockConfigServer, MockDeploymentServer,
│       │                           # BlotterDevServer, ConfigDevServer, DeploymentDevServer
│       ├── js/                     # JavaScript probe workspace (npm + Jest)
│       │   ├── probes/             # api-discovery, dom-probes, grid-api-probes, …
│       │   └── __tests__/          # Jest unit tests (jsdom)
│       └── resources/
│           ├── features/           # BondBlotter (39), ConfigService (14), Deployment (15),
│           │                       # finance_demo, PortfolioRegression
│           ├── wiremock/__files/   # Pre-built blotter Vite assets (git-committed)
│           ├── config-service-ui/  # Pre-built Config Service UI (git-committed)
│           └── deployment-ui/      # Pre-built Deployment Dashboard UI (git-committed)
│
├── pt-blotter-regression-template/ ← Copy-adapt starter for real-system consumers (25 scenarios)
│   └── src/test/
│       ├── java/
│       │   ├── descriptors/{BlotterDescriptor,ConfigServiceDescriptor,DeploymentDescriptor}.java
│       │   ├── stepdefs/{Hooks,BlotterSteps,AppPreconditionSteps,
│       │   │            ConfigServiceSteps,DeploymentSteps,RestApiSteps}.java
│       │   └── utils/{PtBlotterDsl,ConfigServiceDsl,DeploymentDsl}.java
│       └── resources/
│           ├── application-mockuat.conf             ← all three mock servers + full test-data block
│           ├── application-devserver.conf           ← blotter-only smoke (port 9099)
│           ├── templates/{credit-rfq,portfolio-rfq,quote-inquiry}.json
│           └── features/PtBlotterRegression.feature ← 25 runnable scenarios
│
├── BLOTTER_DESIGN.md               # PT-Blotter design doc (milestones M0–M8)
├── MODULARISATION_DESIGN.md        # Multi-module architecture design record
├── CLAUDE.md                       # AI assistant rules (AG Grid patterns, probe arch)
└── pom.xml                         # Parent aggregator — version management for all modules
```

---

## What this demonstrates

| Capability | Implementation |
|---|---|
| Publishable core library | `b-bot-core` — zero Cucumber dependency; consumers add it as a single `<dependency>` |
| HOCON layered config | `BBotConfig` — 5-layer loading; all timeouts/browser settings overridable per environment |
| Component registry | `BBotRegistry` + `AppDescriptor` — register apps, resolve contexts, create DSLs |
| BDD with living documentation | Cucumber 7 feature files as acceptance criteria |
| Playwright + Java browser automation | Page Object Model, role-based locators, no `Thread.sleep` |
| Live-ticking cell handling | `TickingCellHelper` — `waitForFunction` polling, flash-class detection |
| AG Grid virtualisation | `GridHarness` — 3-phase scroll strategy (DOM → JS API → scroll-probe) |
| Industrial JS probes | Named probe modules in `b-bot-sandbox/src/test/js/` tested with Jest + jsdom |
| WireMock mock server | Embedded, classpath-based file serving, dynamic port |
| Config Service microservice | JDK `HttpServer` mock, in-memory CRUD, CORS headers |
| Access-controlled UI | RELEASE PT button gated by `isPTAdmin` from Config Service |
| Deployment Dashboard | AG Grid service registry, 12 seeded services, filter |
| Version-gated regression | `@precondition` scenario asserts deployed versions |
| Copy-adapt template | `pt-blotter-regression-template` — 25-scenario working demo; copy-adapt for real UAT systems |

---

## Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Browser automation | Playwright for Java | 1.49.0 |
| BDD framework | Cucumber | 7.18.1 |
| Test runner | JUnit 5 Platform Suite | 5.10.3 / 1.10.3 |
| Config | Typesafe Config (HOCON) | 1.4.3 |
| REST mock | WireMock | 3.5.4 |
| JSON parsing | Jackson Databind | 2.17.2 |
| Assertions | AssertJ | 3.26.3 |
| Build | Maven | 3.x |
| JS probe tests | Jest + jsdom | 29.x |

---

## Running the sandbox suite (66 scenarios)

### Prerequisites

1. **Java 21+** (`JAVA_HOME` set).
2. **Maven 3.x** on your PATH.
3. **Playwright browsers** — download once:
   ```bash
   mvn test-compile -pl b-bot-sandbox -Dplaywright.install.skip=false
   ```

### Run all 66 scenarios

```bash
mvn verify                           # uses committed Vite assets — no Node needed
mvn verify -Dblotter.build.skip=false  # rebuild the React app too (requires Node 20+)
```

### Headed browser

```bash
mvn verify -Db-bot.browser.headless=false    # preferred (HOCON config)
mvn verify -DHEADLESS=false                  # legacy alias (still works)
```

### Filter by tag

```bash
# PT-Blotter (39 scenarios)
mvn verify -Dcucumber.filter.tags="@blotter and @smoke"   # 1/1 — no Vite build needed
mvn verify -Dcucumber.filter.tags="@blotter and @api"     # 2/2
mvn verify -Dcucumber.filter.tags="@blotter and @ticking" # 3/3
mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m8"  # 5/5

# Config Service (14 scenarios — REST only, no browser)
mvn verify -Dcucumber.filter.tags="@config-service"

# Deployment Dashboard (15 scenarios)
mvn verify -Dcucumber.filter.tags="@deployment"
mvn verify -Dcucumber.filter.tags="@precondition"         # 1/1 — version gate

# Finance Demo / Portfolio (live internet required for @external)
mvn verify -Dcucumber.filter.tags="@ticking"
mvn verify -Dcucumber.filter.tags="@portfolio and not @external"
```

### JavaScript probe unit tests

```bash
cd b-bot-sandbox/src/test/js
npm install        # once — requires Node.js 18+
npm test           # ~60 unit tests across 5 probe modules
```

### Reports (after `mvn verify`)

| Path | Description |
|---|---|
| `b-bot-sandbox/target/cucumber-html-reports/overview-features.html` | Rich HTML dashboard |
| `b-bot-sandbox/target/cucumber-reports/report.json` | Raw JSON for CI ingestion |

---

## Running the PT-Blotter interactively

Start the WireMock-backed mock server and open the React blotter in a browser:

```bash
# Step 1 — build the React app once (downloads Node automatically)
mvn test-compile -pl b-bot-sandbox -Dblotter.build.skip=false

# Step 2 — start the mock server (from project root)
mvn exec:java -pl b-bot-sandbox \
    -Dexec.mainClass=utils.BlotterDevServer \
    -Dexec.classpathScope=test

# Step 3 — open in browser
# http://localhost:9099/blotter/
```

Press **ENTER** in the terminal to stop the server.

Similarly for the Config Service (`utils.ConfigDevServer`, port 8090) and
Deployment Dashboard (`utils.DeploymentDevServer`, port 9098).

---

## Using pt-blotter-regression-template

Copy the directory, rename it, and adapt it for your real system.
See [`pt-blotter-regression-template/README.md`](pt-blotter-regression-template/README.md) for the step-by-step guide.

```bash
# Quick smoke against the DevServer (after starting BlotterDevServer above)
mvn verify -pl pt-blotter-regression-template \
    -Db-bot.env=devserver \
    -Dcucumber.filter.tags="@smoke"
```

---

## Key design decisions

### Configurable via HOCON — no magic constants in code

All browser settings and timeouts are declared in `b-bot-core`'s `reference.conf`
with sensible defaults. Override any value without code changes:

```hocon
# application-uat.conf
b-bot {
  browser    { headless = false }
  timeouts   { navigation = 60s, gridRender = 20s }
  apps.blotter {
    webUrl  = "https://blotter.uat.example.com/blotter/"
    apiBase = "https://blotter.uat.example.com"
  }
}
```

### No `Thread.sleep()` — ever

All waits use Playwright's built-in retry mechanism:
`page.waitForFunction()`, `assertThat(locator).isVisible()`, `locator.waitFor()`.

### Industrial JS probe architecture

All JavaScript is in named, testable modules under `b-bot-sandbox/src/test/js/probes/`.
The bundle is injected into every Playwright context via `addInitScript()` so
`window.agGridProbes.*` is always available before page scripts run.

### AG Grid virtualisation — three-phase strategy

`GridHarness.findRowByCellValue()` handles rows that have scrolled out of view:
1. **Fast-path** — DOM scan (configurable timeout, default 500 ms).
2. **Grid API** — `agGridProbes.gridApi.findRowIndexByDataValue()` against the data model,
   then `ensureRowVisible()`.
3. **Scroll-probe** — reset to top, page down, `waitForFunction` for new rows.

### WireMock — classpath-based file serving

`MockBlotterServer` uses `usingFilesUnderClasspath("wiremock")` so it works from
any working directory (Maven reactor root or module directory).

---

## CI/CD integration

Test output is standard JUnit XML (`target/surefire-reports/`) and Cucumber JSON
(`target/cucumber-reports/report.json`), compatible with Jenkins, GitHub Actions,
GitLab CI, and any CI system that consumes these formats.

```yaml
# GitHub Actions example
- name: Run sandbox regression suite
  run: mvn verify

- name: Publish Cucumber report
  uses: actions/upload-artifact@v4
  with:
    name: cucumber-report
    path: b-bot-sandbox/target/cucumber-html-reports/
```
