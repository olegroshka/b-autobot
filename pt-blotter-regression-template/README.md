# pt-blotter-regression-template

A **copy-and-adapt** starting point for building a real-system BDD regression suite on top of [`b-bot-core`](../b-bot-core/README.md).

> **This is a template, not a library.**
> Copy this directory into your own project, rename it, and adapt it.
> The template's PT-Blotter wiring is just an example — replace it with your own application's DSL and scenarios.

---

## What you get out of the box

| File | Purpose |
|------|---------|
| `Hooks.java` | Wires `BBotRegistry` + `PlaywrightManager`; one `@BeforeAll`/`@AfterAll` |
| `BlotterDescriptor.java` | Example `AppDescriptor` — declares the app name, DSL factory, health/version paths |
| `PtBlotterDsl.java` | Example DSL — all Playwright calls in one place; URLs injected from config |
| `BlotterSteps.java` | Example step definitions — delegates 100% to the DSL |
| `AppPreconditionSteps.java` | Generic health + version precondition steps (ready to use as-is) |
| `Smoke.feature` | Minimal smoke scenario to verify the wiring is correct |
| `application.conf` | Base config (shared settings across all environments) |
| `application-devserver.conf` | Local dev-server environment pointing at `localhost:9099` |
| `cucumber.properties` | Cucumber JUnit Platform Engine settings |

---

## Prerequisites

1. **Java 21+** and **Maven 3.8+**
2. **b-bot-core** installed in your local Maven repository:
   ```
   # from the b-autobot root
   mvn install -pl b-bot-core -am -DskipTests
   ```
3. **Chromium** (installed by Playwright's CLI, once):
   ```
   mvn test-compile -pl pt-blotter-regression-template -Dplaywright.install.skip=false
   ```

---

## Project structure

```
pt-blotter-regression-template/
├── pom.xml
└── src/test/
    ├── java/
    │   ├── descriptors/
    │   │   └── BlotterDescriptor.java   ← declare your app here
    │   ├── runners/
    │   │   └── TestRunner.java          ← JUnit Platform Suite runner
    │   ├── stepdefs/
    │   │   ├── AppPreconditionSteps.java ← health/version steps (keep as-is)
    │   │   ├── BlotterSteps.java        ← Gherkin step definitions
    │   │   └── Hooks.java               ← framework lifecycle
    │   └── utils/
    │       └── PtBlotterDsl.java        ← all Playwright interactions live here
    └── resources/
        ├── application.conf             ← base config
        ├── application-devserver.conf   ← local dev server
        ├── application-uat.conf         ← (create this for UAT)
        ├── cucumber.properties
        └── features/
            └── Smoke.feature            ← replace / extend with real scenarios
```

---

## Quick start — run the smoke test against the dev server

```bash
# Terminal 1: start the PT-Blotter mock server
cd b-bot-sandbox
mvn exec:java -Dexec.mainClass=utils.BlotterDevServer -Dexec.classpathScope=test

# Terminal 2: run the smoke test
cd pt-blotter-regression-template
mvn verify -Db-bot.env=devserver -Dcucumber.filter.tags="@smoke"
```

Expected output:
```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Configuring your own environment

Create `src/test/resources/application-uat.conf` (not committed — keep secrets in CI):

```hocon
b-bot {
  apps {
    blotter {
      webUrl  = "https://blotter.uat.yourcompany.com/blotter/"
      apiBase = "https://blotter.uat.yourcompany.com"
      users {
        trader = jsmith
        admin  = agarcia
      }
    }
  }
}
```

Run against UAT:
```bash
mvn verify -Db-bot.env=uat -Dcucumber.filter.tags="@smoke"
```

### All browser / timeout defaults come from b-bot-core

`b-bot-core`'s `reference.conf` supplies sensible defaults for every setting.
Override only what you need in your environment conf:

```hocon
# application-uat.conf
b-bot {
  browser {
    headless = false           # open a real window in UAT to watch it run
    viewport { width = 1440, height = 900 }
  }
  timeouts {
    navigation  = 60s          # UAT has higher network latency
    gridRender  = 20s
    apiResponse = 15s
  }
  apps {
    blotter {
      webUrl  = "https://blotter.uat.yourcompany.com/blotter/"
      apiBase = "https://blotter.uat.yourcompany.com"
    }
  }
}
```

Full list of configurable keys: see [`b-bot-core/src/main/resources/reference.conf`](../b-bot-core/src/main/resources/reference.conf).

---

## Adding your first real scenario

1. **Write the scenario** in `src/test/resources/features/YourFeature.feature`:
   ```gherkin
   @smoke @your-tag
   Scenario: Trade blotter shows open trades
     Given the "blotter" app is healthy
     And the PT-Blotter is open
     Then the blotter grid should be visible
   ```

2. **Add DSL methods** in `PtBlotterDsl.java` for each new step:
   ```java
   public int countRows() {
       return page.locator(".ag-center-cols-container [row-index]").count();
   }
   ```

3. **Add step definitions** in `BlotterSteps.java`:
   ```java
   @Then("the grid has at least {int} row(s)")
   public void gridHasAtLeastRows(int min) {
       assertThat(blotter.countRows()).isGreaterThanOrEqualTo(min);
   }
   ```

4. Run:
   ```bash
   mvn verify -Db-bot.env=devserver -Dcucumber.filter.tags="@your-tag"
   ```

---

## Adding a second application

1. Create `descriptors/MyServiceDescriptor.java` implementing `AppDescriptor<MyServiceDsl>`.
2. Create `utils/MyServiceDsl.java` with your service's DSL methods.
3. Register in `Hooks.java`: `BBotRegistry.register(new MyServiceDescriptor());`
4. Add URLs to your environment conf:
   ```hocon
   b-bot.apps.my-service {
     apiBase = "https://my-service.uat.yourcompany.com"
   }
   ```
5. In step definitions: `BBotRegistry.dsl("my-service", null, MyServiceDsl.class)`
   (`null` for page since it's a REST-only service).

---

## Useful Maven commands

```bash
# Compile only (fast validation — no server needed)
mvn test-compile

# Smoke test against dev server
mvn verify -Db-bot.env=devserver -Dcucumber.filter.tags="@smoke"

# All scenarios against UAT
mvn verify -Db-bot.env=uat

# Headed browser (watch Chromium open)
mvn verify -Db-bot.env=devserver -Db-bot.browser.headless=false

# Specific tag
mvn verify -Db-bot.env=uat -Dcucumber.filter.tags="@regression and not @wip"
```
