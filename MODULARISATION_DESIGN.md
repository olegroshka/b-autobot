# Modularisation Design — b-autobot → b-bot-core + b-bot-sandbox

## Context & Motivation

We have built a mature, 66-scenario BDD regression suite targeting three mock web apps
(PT-Blotter, Config Service, Deployment Dashboard) plus the live AG Grid Finance Demo.
The infrastructure we built — the JS probe bundle, `GridHarness`, `TickingCellHelper`,
`PlaywrightManager`, the DSL pattern — is generic and valuable far beyond these demos
and beyond grids specifically.

The goal of this document is to extract that infrastructure into a publishable library
(`b-bot-core`) while keeping the demo apps and their scenarios in a self-contained
reference module (`b-bot-sandbox`), and making it straightforward for the real
PT-Blotter regression project (and any future consumer) to adopt the library against
live UAT / pre-prod / prod environments with zero framework code duplication.

---

## Confirmed Decisions

| Decision | Resolved |
|----------|----------|
| Module naming | `b-bot-core` / `b-bot-sandbox` |
| Group ID | `com.bbot` — package-safe (hyphens cannot appear in Java package names) |
| HOCON config namespace | `b-bot.*` — keeps naming consistent across config and module identity |
| Playwright scope in core | `compile` — exported transitively; core's API surface uses `Page`, `Locator` |
| Cucumber in core | **No** — zero Cucumber dependency in core; lifecycle hooks stay in consuming module |
| Config format | **Typesafe Config (HOCON)** — hierarchical, typed, env-variable interpolating |
| Secrets management | Deferred to M6 (real auth requirements unknown until we see the system) |
| bundle.js sync discipline | M1: automate the concatenation of the 6 probe modules into `bundle.js` |

---

## What Has Been Built — Component Inventory

### Reusable → `b-bot-core`

| Component | Current location | Notes |
|-----------|-----------------|-------|
| Playwright lifecycle | `utils/PlaywrightManager.java` | Thread-local; zero app knowledge |
| JS probe bundle | `src/test/js/probes/bundle.js` + 6 source modules | Works against any instrumentable browser app |
| Probe loader | `utils/ProbesLoader.java` | Generic classpath resource loader |
| Virtualisation-safe row finder | `utils/GridHarness.java` | App-agnostic; works on any AG Grid |
| Ticking cell helpers | `utils/TickingCellHelper.java` | App-agnostic |
| Numeric comparator | `utils/NumericComparator.java` | General-purpose |
| DSL skeleton | patterns in `BlotterDsl`, `ConfigServiceDsl`, `DeploymentDsl` | The *pattern*, not the content |
| Generic HTTP helpers | inner methods in `ConfigServiceDsl` / `DeploymentDsl` | Generic GET/PUT/DELETE wrappers |

### Application-Specific → stays in `b-bot-sandbox`

| Component | Current location | Notes |
|-----------|-----------------|-------|
| Mock servers | `utils/Mock*Server.java` | Domain seed data, WireMock stubs |
| Concrete DSLs | `utils/*Dsl.java` | Must be decoupled from static URL calls (see below) |
| Lifecycle hooks | `stepdefs/Hooks.java` | Starts/stops the three specific mock servers |
| Feature files | `resources/features/*.feature` | Demo scenarios |
| Step definitions | `stepdefs/*Steps.java` | Domain-specific language |
| Model classes | `model/Trade.java`, `TradePortfolio.java` | Blotter domain model |
| React webapps | `webapp/`, `webapp-config/`, `webapp-deployment/` | Demo UI source |
| Dev servers | `utils/*DevServer.java` | Manual exploration tools |

### The Central Coupling Problem

Every concrete DSL calls a mock-server static method to get its base URL:

```java
// BlotterDsl — hardwired to two mock servers
String url = MockBlotterServer.getBlotterUrl()
           + "?user=" + user
           + "&configUrl=" + MockConfigServer.getBaseUrl();  // cross-app static call

// ConfigServiceDsl
URI.create(MockConfigServer.getBaseUrl() + path)

// DeploymentDsl
URI.create(MockDeploymentServer.getBaseUrl() + path)
```

Against a **real system** there are no mock servers. URLs vary per environment and
are supplied by ops/infra at test-run time. This coupling must be completely severed.

---

## Approach 1 — Maven Multi-Module + Flat Properties Files

*(Kept as reference — not the recommended direction.)*

Config via `.properties` files selected by `-Db-bot.env=uat`. Simple, zero new deps.

**Main weakness:** flat properties have no inheritance — no way to express "UAT inherits
base defaults except for these 3 URLs" without duplicating every key. This becomes
painful as soon as you have 4+ environments and grows unbounded.

---

## Approach 2 — App-Descriptor-Driven Architecture with HOCON (Recommended)

### Core Philosophy

Rather than the framework knowing about specific applications (blotter, config service,
deployment dashboard), we invert the dependency: **each tested component describes itself
to the framework via an `AppDescriptor`**. The framework knows only about descriptors;
it never knows about `BlotterDsl` or `MockBlotterServer`.

Three concepts drive the entire design:

```
AppDescriptor  — supplies only a DslFactory; app name, health/version paths live in HOCON config
AppContext      — what the framework KNOWS about a component at runtime (URLs, users, timeouts)
BBotSession    — immutable session: holds descriptors + contexts; auto-discovers from config
BBotRegistry   — thin static facade over BBotSession (@Deprecated statics; prefer BBotSession directly)
```

HOCON provides URLs and settings per environment, keyed by component name under `b-bot.apps.*`.
The sandbox overrides dynamic mock-server ports at `@BeforeAll` time via an immutable
runtime override mechanism layered on top of the HOCON config.

---

### Module Structure

```
b-autobot/                             ← parent POM (aggregator, no sources)
├── pom.xml                            ← dependencyManagement, pluginManagement
│
├── b-bot-core/                        ← publishable library; no Cucumber dependency
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/bbot/core/
│       │   ├── PlaywrightManager.java
│       │   ├── ProbesLoader.java
│       │   ├── GridHarness.java
│       │   ├── TickingCellHelper.java
│       │   ├── NumericComparator.java
│       │   ├── registry/
│       │   │   ├── AppDescriptor.java    ← @FunctionalInterface: supplies only dslFactory()
│       │   │   ├── AppContext.java       ← value: resolved URLs + users + timeouts per app
│       │   │   ├── DslFactory.java       ← functional interface: (AppContext, Page) -> D
│       │   │   ├── BBotSession.java      ← immutable session; auto-discovers descriptor-class from config
│       │   │   └── BBotRegistry.java     ← @Deprecated static facade over BBotSession
│       │   └── config/
│       │       └── BBotConfig.java       ← HOCON loader; runtime override support
│       └── resources/
│           ├── js/probes/bundle.js       ← on classpath for ProbesLoader
│           └── reference.conf           ← lowest-priority core defaults (browser, timeouts)
│
├── b-bot-sandbox/                     ← demo & regression suite (66 scenarios)
│   ├── pom.xml                        ← depends on b-bot-core
│   └── src/test/
│       ├── java/
│       │   ├── descriptors/           ← one AppDescriptor impl per app
│       │   │   ├── BlotterAppDescriptor.java
│       │   │   ├── ConfigServiceDescriptor.java
│       │   │   └── DeploymentDescriptor.java
│       │   ├── stepdefs/              ← domain steps + Hooks
│       │   ├── utils/                 ← Mock*Server, *Dsl (URL-injected via AppContext)
│       │   └── runners/TestRunner.java
│       ├── js/                        ← JavaScript probe workspace (npm + Jest)
│       │   ├── probes/                ← source probe modules + bundle.js
│       │   └── __tests__/             ← Jest unit tests (jsdom)
│       └── resources/
│           ├── features/
│           ├── application.conf       ← sandbox non-URL settings (users, timeouts)
│           ├── wiremock/              ← WireMock stubs + blotter Vite build
│           ├── config-service-ui/     ← pre-built Config Service UI
│           └── deployment-ui/         ← pre-built Deployment Dashboard UI
│
└── pt-blotter-regression-template/   ← copy-adapt starter for real-system consumers
    ├── pom.xml                        ← depends on b-bot-core; skipTests=true by default
    └── src/test/
        ├── java/
        │   ├── descriptors/BlotterDescriptor.java
        │   ├── stepdefs/{Hooks,BlotterSteps,AppPreconditionSteps}.java
        │   └── utils/PtBlotterDsl.java
        └── resources/
            ├── application.conf             ← base config + commented override examples
            ├── application-devserver.conf   ← points at localhost:9099
            └── features/Smoke.feature
```

---

### Core API — Full Interface Definitions

#### `DslFactory.java`

```java
package com.bbot.core.registry;

import com.microsoft.playwright.Page;

/**
 * Creates the DSL for one tested component, given its resolved runtime context
 * and (for WEB_APP components) the current scenario's Playwright Page.
 *
 * Page is per-scenario (created in @Before); AppContext is per-environment
 * (resolved once in @BeforeAll). Keeping them separate lets the same factory
 * be registered once and called fresh each scenario.
 */
@FunctionalInterface
public interface DslFactory<D> {
    /**
     * @param ctx  resolved environment context — never null
     * @param page current scenario's Playwright page — null for REST_API-only apps
     */
    D create(AppContext ctx, Page page);
}
```

#### `AppDescriptor.java`

```java
package com.bbot.core.registry;

/**
 * Supplies only a DslFactory. All other app metadata lives in HOCON config:
 *
 *   b-bot.apps.blotter {
 *     descriptor-class  = "com.bbot.sandbox.descriptors.BlotterAppDescriptor"
 *     health-check-path = "/api/health"
 *     version-path      = "/api/version"   // optional
 *     webUrl  = "http://..."
 *     apiBase = "http://..."
 *   }
 *
 * BBotSession.Builder.initialize(cfg) reads descriptor-class, instantiates the
 * descriptor via a public no-arg constructor, and registers it automatically.
 *
 * @param <D> the DSL type this descriptor produces
 */
@FunctionalInterface
public interface AppDescriptor<D> {

    /** Factory that constructs a fresh DSL instance for each scenario. */
    DslFactory<D> dslFactory();
}
```

#### `AppContext.java`

```java
package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Per-environment, per-app resolved runtime context.
 *
 * Created once per registered app during BBotRegistry.initialize(BBotConfig).
 * Passed to every DslFactory.create() call, giving the DSL everything it needs:
 * resolved URL(s), users, timeouts — zero static mock-server calls.
 *
 * HOCON source paths (all under b-bot.apps.{name}):
 *   .webUrl          → getWebUrl()        — ends with '/', null if app is REST_API only
 *   .apiBase         → getApiBaseUrl()    — no trailing slash, null if app is WEB_APP only
 *   .users.*         → getUser(role)
 *   .versions.*      → getExpectedVersion(serviceName)
 *   b-bot.timeouts.* → getTimeout(key)
 */
public final class AppContext {

    private final String name;
    private final String webUrl;
    private final String apiBaseUrl;
    private final Map<String, String> users;
    private final Map<String, String> versions;
    private final BBotConfig config;

    // Package-private: only BBotRegistry constructs these
    AppContext(String name, String webUrl, String apiBaseUrl,
               Map<String, String> users, Map<String, String> versions,
               BBotConfig config) {
        this.name       = name;
        this.webUrl     = webUrl;
        this.apiBaseUrl = apiBaseUrl;
        this.users      = Map.copyOf(users);
        this.versions   = Map.copyOf(versions);
        this.config     = config;
    }

    public String           name()                                   { return name; }
    public String           getWebUrl()                              { return webUrl; }
    public String           getApiBaseUrl()                          { return apiBaseUrl; }
    public Optional<String> getUser(String role)                     { return Optional.ofNullable(users.get(role)); }
    public Optional<String> getExpectedVersion(String serviceName)   { return Optional.ofNullable(versions.get(serviceName)); }
    public Duration         getTimeout(String key)                   { return config.getTimeout(key); }

    /**
     * Cross-app URL access — blotter needs config-service's apiBase to build its startup URL.
     * Reading it from the shared BBotConfig is safe: it's a config lookup, not a class coupling.
     */
    public String getOtherAppApiBase(String otherAppName) {
        return config.getAppApiBase(otherAppName);
    }

    /** Full config access for anything not covered by typed accessors. */
    public BBotConfig config() { return config; }
}
```

#### `BBotRegistry.java`

```java
package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;
import com.microsoft.playwright.Page;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all AppDescriptors.
 *
 * Lifecycle (all called from the consuming module's Hooks.java):
 *   1. register(descriptor)    — once per app during @BeforeAll
 *   2. initialize(config)      — once after all servers started; resolves AppContexts
 *   3. dsl(name, page, class)  — per scenario; creates a fresh DSL via DslFactory
 *   4. checkHealth(name)       — per precondition; asserts liveness via health endpoint
 *   5. assertVersion(name, v)  — per precondition; asserts deployed version
 *   6. reset()                 — in @AfterAll to clear state for the next JVM run
 */
public final class BBotRegistry {

    private static final Map<String, AppDescriptor<?>> DESCRIPTORS = new LinkedHashMap<>();
    private static final Map<String, AppContext>        CONTEXTS    = new ConcurrentHashMap<>();
    private static BBotConfig config;

    private BBotRegistry() {}

    // ── Registration ─────────────────────────────────────────────────────────

    public static void register(AppDescriptor<?> descriptor) {
        DESCRIPTORS.put(descriptor.name(), descriptor);
    }

    /**
     * Resolves an AppContext for every registered descriptor from the supplied config.
     * Call once in @BeforeAll after all dynamic-port servers have started and
     * any runtime URL overrides have been applied via BBotConfig.withOverrides().
     */
    public static void initialize(BBotConfig cfg) {
        config = cfg;
        DESCRIPTORS.forEach((name, desc) ->
            CONTEXTS.put(name, AppContext.fromConfig(name, cfg)));
    }

    // ── DSL factory ───────────────────────────────────────────────────────────

    /**
     * Returns a fresh DSL for the named app, bound to the current scenario's page.
     * Invokes the descriptor's DslFactory — one fresh instance per scenario.
     *
     * @param appName  registered name, e.g. "blotter"
     * @param page     current scenario's Playwright page; null for REST-only apps
     * @param dslType  DSL class for type-safe cast (prevents accidental miscast)
     */
    @SuppressWarnings("unchecked")
    public static <D> D dsl(String appName, Page page, Class<D> dslType) {
        AppDescriptor<D> desc = (AppDescriptor<D>) requireDescriptor(appName);
        AppContext ctx = requireContext(appName);
        return desc.dslFactory().create(ctx, page);
    }

    // ── Health / version assertions ───────────────────────────────────────────

    /**
     * Asserts the named app's health endpoint returns 2xx.
     * No-op if the descriptor declares no healthCheckPath.
     */
    public static void checkHealth(String appName) {
        AppDescriptor<?> desc = requireDescriptor(appName);
        AppContext ctx = requireContext(appName);
        desc.healthCheckPath().ifPresent(path -> {
            String url = ctx.getApiBaseUrl() + path;
            int status = httpGetStatus(url);
            if (status < 200 || status >= 300) {
                throw new AssertionError(
                    "Health check FAILED for '" + appName + "': " +
                    "GET " + url + " returned HTTP " + status);
            }
        });
    }

    /**
     * Asserts the named app's version endpoint returns JSON containing the expected version.
     * No-op if the descriptor declares no versionPath.
     */
    public static void assertVersion(String appName, String expectedVersion) {
        AppDescriptor<?> desc = requireDescriptor(appName);
        AppContext ctx = requireContext(appName);
        desc.versionPath().ifPresent(path -> {
            String body = httpGetBody(ctx.getApiBaseUrl() + path);
            if (!body.contains("\"" + expectedVersion + "\"")) {
                throw new AssertionError(
                    "Version mismatch for '" + appName + "': expected '" +
                    expectedVersion + "' but response was: " + body);
            }
        });
    }

    /** Checks health of all registered apps that declare a healthCheckPath. */
    public static void checkAllHealth() {
        DESCRIPTORS.keySet().forEach(BBotRegistry::checkHealth);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void reset() {
        DESCRIPTORS.clear();
        CONTEXTS.clear();
        config = null;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static AppDescriptor<?> requireDescriptor(String name) {
        AppDescriptor<?> d = DESCRIPTORS.get(name);
        if (d == null) throw new IllegalArgumentException(
            "No AppDescriptor registered for '" + name + "'. " +
            "Registered names: " + DESCRIPTORS.keySet());
        return d;
    }

    private static AppContext requireContext(String name) {
        AppContext ctx = CONTEXTS.get(name);
        if (ctx == null) throw new IllegalStateException(
            "BBotRegistry not initialised. " +
            "Call BBotRegistry.initialize(config) in @BeforeAll " +
            "after all servers are started.");
        return ctx;
    }

    private static int httpGetStatus(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            throw new AssertionError("Health check HTTP request failed: " + url, e);
        }
    }

    private static String httpGetBody(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            return client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            throw new AssertionError("Version check HTTP request failed: " + url, e);
        }
    }
}
```

---

### `BBotConfig.java` — HOCON Loader with Runtime Override Support

```java
package com.bbot.core.config;

import com.typesafe.config.*;
import java.time.Duration;
import java.util.*;

/**
 * Typesafe Config (HOCON) wrapper with layered environment support and
 * runtime override capability for dynamic-port test servers.
 *
 * All config keys use the "b-bot" namespace (e.g. b-bot.apps.blotter.webUrl).
 *
 * Loading order (highest priority wins):
 *   1. Runtime overrides      — BBotConfig.withOverrides(Map) at @BeforeAll time
 *   2. JVM system properties  — -Db-bot.apps.blotter.webUrl=...
 *   3. application-{env}.conf — environment layer (classpath, consuming module)
 *   4. application.conf       — consumer base defaults (classpath, consuming module)
 *   5. reference.conf         — core defaults (inside b-bot-core.jar; lowest priority)
 *
 * Active environment:
 *   System.getProperty("b-bot.env")  or  env var B_BOT_ENV  (default: "local")
 *
 * Usage in sandbox Hooks.java:
 *   BBotConfig cfg = BBotConfig.load()
 *       .withOverrides(Map.of(
 *           "b-bot.apps.blotter.webUrl", MockBlotterServer.getBlotterUrl(),
 *           "b-bot.apps.blotter.apiBase", MockBlotterServer.getBaseUrl()
 *       ));
 */
public final class BBotConfig {

    private final Config cfg;

    private BBotConfig(Config cfg) { this.cfg = cfg; }

    public static BBotConfig load() {
        String env = System.getProperty("b-bot.env",
                     System.getenv().getOrDefault("B_BOT_ENV", "local"));

        Config reference = ConfigFactory.defaultReference();   // reference.conf in core JAR
        Config appBase   = ConfigFactory.parseResources("application.conf")
                               .withFallback(reference);
        Config layered   = env.equals("local")
                ? appBase
                : ConfigFactory.parseResources("application-" + env + ".conf")
                      .withFallback(appBase);

        return new BBotConfig(
            ConfigFactory.systemProperties()
                .withFallback(layered)
                .resolve()
        );
    }

    /** Returns a new BBotConfig with the given key-value pairs layered on top. Immutable. */
    public BBotConfig withOverrides(Map<String, String> overrides) {
        return new BBotConfig(
            ConfigFactory.parseMap(overrides).withFallback(cfg).resolve()
        );
    }

    // ── Typed accessors ───────────────────────────────────────────────────────

    public String   getString(String key)   { return cfg.getString(key); }
    public boolean  getBoolean(String key)  { return cfg.getBoolean(key); }
    public Duration getTimeout(String key)  { return cfg.getDuration(key); }
    public boolean  hasPath(String key)     { return cfg.hasPath(key); }

    /** webUrl for the named app. Convention: always ends with '/'. Null if not configured. */
    public String getAppWebUrl(String appName) {
        String key = "b-bot.apps." + appName + ".webUrl";
        return cfg.hasPath(key) ? cfg.getString(key) : null;
    }

    /** apiBase for the named app. Convention: never has trailing slash. Null if not configured. */
    public String getAppApiBase(String appName) {
        String key = "b-bot.apps." + appName + ".apiBase";
        return cfg.hasPath(key) ? cfg.getString(key) : null;
    }

    /** User map for the named app (role -> username). Empty map if not configured. */
    public Map<String, String> getAppUsers(String appName) {
        String key = "b-bot.apps." + appName + ".users";
        if (!cfg.hasPath(key)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        cfg.getConfig(key).entrySet().forEach(e ->
            result.put(e.getKey(), e.getValue().unwrapped().toString()));
        return Collections.unmodifiableMap(result);
    }

    /** Version map for the named app (service-name -> expected-version). */
    public Map<String, String> getAppVersions(String appName) {
        String key = "b-bot.apps." + appName + ".versions";
        if (!cfg.hasPath(key)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        cfg.getConfig(key).entrySet().forEach(e ->
            result.put(e.getKey(), e.getValue().unwrapped().toString()));
        return Collections.unmodifiableMap(result);
    }

    // ── App-descriptor discovery (M14) ────────────────────────────────────────

    /** All configured app names under b-bot.apps. */
    public Set<String> getAppNames() { ... }

    /** b-bot.apps.{name}.descriptor-class FQCN, or empty if not set. */
    public Optional<String> getAppDescriptorClass(String appName) { ... }

    /** b-bot.apps.{name}.health-check-path, or empty if not set. */
    public Optional<String> getAppHealthCheckPath(String appName) { ... }

    /** b-bot.apps.{name}.version-path, or empty if not set. */
    public Optional<String> getAppVersionPath(String appName) { ... }

    /** Raw HOCON access for anything not covered by the typed accessors. */
    public Config raw() { return cfg; }
}
```

---

### HOCON Config Files

#### `reference.conf` — inside `b-bot-core` JAR (lowest priority, always present)

```hocon
# b-bot-core defaults. These have the lowest priority — everything can be overridden.
# Every consumer of b-bot-core gets these defaults automatically via ConfigFactory.defaultReference().
b-bot {
  browser {
    type     = chromium
    headless = true
    viewport { width = 1920, height = 1080 }
  }
  timeouts {
    navigation     = 30s    # page.navigate() / waitForLoadState() budget
    cellFlash      = 3s     # wait for ag-cell-data-changed CSS class to appear
    gridRender     = 10s    # wait for AG Grid to show at least one row
    apiResponse    = 10s    # HTTP budget for DSL REST calls
    gridFastPath   = 500ms  # phase-1: fast DOM scan timeout
    gridRowInDom   = 5s     # phase-2: confirm row in DOM after ensureIndexVisible
    gridHasRows    = 5s     # phase-3: wait for any row before scroll-probe starts
    gridScrollStep = 2s     # phase-3: per-step scroll wait
    healthCheck    = 10s    # connect + response timeout for BBotRegistry probes
  }
  grid {
    renderPollMs   = 100    # waitForFunction poll interval (ms) in GridHarness
    maxScrollSteps = 200    # max scroll iterations before GridHarness gives up
  }
  ticking {
    pollMs = 150            # waitForFunction poll interval (ms) for ticking cells
  }
  apps {}
}
```

#### `application.conf` — `b-bot-sandbox/src/test/resources/`

```hocon
# Sandbox base config.
# URLs are NOT hardcoded here — mock server ports are dynamic and injected
# at @BeforeAll time via BBotConfig.withOverrides() in Hooks.java.

b-bot {
  env = local

  apps {
    blotter {
      # webUrl and apiBase: injected at runtime — see sandbox Hooks.java
      descriptor-class  = "com.bbot.sandbox.descriptors.BlotterAppDescriptor"
      health-check-path = "/api/health"
      users { trader = doej, admin = smithj }
    }
    config-service {
      # apiBase: injected at runtime
      descriptor-class  = "com.bbot.sandbox.descriptors.ConfigServiceDescriptor"
      health-check-path = "/api/config"
    }
    deployment {
      # webUrl and apiBase: injected at runtime
      descriptor-class  = "com.bbot.sandbox.descriptors.DeploymentDescriptor"
      health-check-path = "/api/deployments"
    }
  }
}
```

#### `application-uat.conf` — `pt-blotter-regression-template/src/test/resources/`

```hocon
# UAT environment — real system endpoints.
# Activate: mvn verify -Db-bot.env=uat   OR   B_BOT_ENV=uat mvn verify

b-bot {
  env = UAT

  # UAT has higher network latency — extend ticking-cell timeout
  timeouts.cellFlash = 5s

  apps {
    blotter {
      webUrl  = "https://uat-blotter.firm.com/"
      apiBase = "https://uat-api.firm.com"
      users   { trader = doej, admin = smithj }
      versions {
        credit-rfq-blotter   = v2.4.1
        credit-pt-pricer     = v1.8.3
        credit-pt-neg-engine = v3.1.0
      }
    }
    config-service {
      apiBase = "https://uat-config.firm.com"
    }
    deployment {
      webUrl  = "https://uat-ops.firm.com/deployment/"
      apiBase = "https://uat-ops.firm.com"
    }
  }

  # CI secret injection — value from env var wins over anything in this file.
  # Set B_BOT_UAT_TOKEN in your CI pipeline secret store; never commit a real value.
  auth.token = ${?B_BOT_UAT_TOKEN}
}
```

#### `application-preprod.conf` — `pt-blotter-regression-template/src/test/resources/`

```hocon
# Pre-prod — inherits all UAT settings, only URLs differ.
include "application-uat.conf"

b-bot {
  env = pre-prod

  apps {
    blotter {
      webUrl  = "https://preprod-blotter.firm.com/"
      apiBase = "https://preprod-api.firm.com"
      # users, versions: inherited from application-uat.conf — DRY
    }
    config-service.apiBase = "https://preprod-config.firm.com"
    deployment {
      webUrl  = "https://preprod-ops.firm.com/deployment/"
      apiBase = "https://preprod-ops.firm.com"
    }
  }
}
```

---

### Descriptor Implementations (in `b-bot-sandbox`)

#### `BlotterAppDescriptor.java`

```java
package com.bbot.sandbox.descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.DslFactory;
import com.bbot.sandbox.utils.BlotterDsl;

/** Supplies only the DSL factory. App name, health path, URLs declared in HOCON. */
public final class BlotterAppDescriptor implements AppDescriptor<BlotterDsl> {

    @Override public DslFactory<BlotterDsl> dslFactory() {
        return (ctx, page) -> new BlotterDsl(page, ctx);
    }
}
```

#### `ConfigServiceDescriptor.java`

```java
package com.bbot.sandbox.descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.DslFactory;
import com.bbot.sandbox.utils.ConfigServiceDsl;

/** REST-only descriptor — page is ignored. App name + health path in HOCON. */
public final class ConfigServiceDescriptor implements AppDescriptor<ConfigServiceDsl> {

    @Override public DslFactory<ConfigServiceDsl> dslFactory() {
        return (ctx, page) -> new ConfigServiceDsl(ctx.getApiBaseUrl());
    }
}
```

#### `DeploymentDescriptor.java`

```java
package com.bbot.sandbox.descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.DslFactory;
import com.bbot.sandbox.utils.DeploymentDsl;

/** Supplies only the DSL factory. App name, health path, URLs declared in HOCON. */
public final class DeploymentDescriptor implements AppDescriptor<DeploymentDsl> {

    @Override public DslFactory<DeploymentDsl> dslFactory() {
        return (ctx, page) -> new DeploymentDsl(page, ctx.getApiBaseUrl());
    }
}
```

---

### Decoupled DSL Constructors

```java
// BEFORE — static coupling to mock servers
public final class BlotterDsl {
    private final Page page;
    public BlotterDsl(Page page) { this.page = page; }

    public void openBlotter(String user) {
        String url = MockBlotterServer.getBlotterUrl()       // STATIC — breaks on real system
                   + "?user=" + user
                   + "&configUrl=" + MockConfigServer.getBaseUrl();  // STATIC cross-app call
        page.navigate(url);
    }
}

// AFTER — all URLs injected via AppContext; zero static server references
public final class BlotterDsl {
    private final Page page;
    private final AppContext ctx;

    public BlotterDsl(Page page, AppContext ctx) {
        this.page = page;
        this.ctx  = ctx;
    }

    public void openBlotter(String user) {
        String url = ctx.getWebUrl()                          // "https://uat-blotter.firm.com/"
                   + "?user=" + user
                   + "&configUrl=" + ctx.getOtherAppApiBase("config-service");
        page.navigate(url);
        page.waitForSelector(".ag-center-cols-container [row-index='0']");
    }
    // All other methods unchanged
}

// ConfigServiceDsl — even simpler, just the baseUrl string
public final class ConfigServiceDsl {
    private final String baseUrl;
    public ConfigServiceDsl(String baseUrl) { this.baseUrl = baseUrl; }

    private String get(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path)).GET().build();  // no MockConfigServer reference
        // ...
    }
}
```

---

### Revised `Hooks.java` (sandbox)

```java
public class Hooks {

    @BeforeAll
    public static void launchBrowser() {
        // 1. Start mock servers (sandbox only — real consumers omit this block entirely)
        MockConfigServer.start();
        MockDeploymentServer.start();
        MockBlotterServer.start();

        // 2. Load base HOCON config, then layer dynamic mock-server ports on top.
        //    withOverrides() is immutable — returns a new BBotConfig instance.
        BBotConfig cfg = BBotConfig.load()
            .withOverrides(Map.of(
                "b-bot.apps.blotter.webUrl",           MockBlotterServer.getBlotterUrl(),
                "b-bot.apps.blotter.apiBase",          MockBlotterServer.getBaseUrl(),
                "b-bot.apps.config-service.apiBase",   MockConfigServer.getBaseUrl(),
                "b-bot.apps.deployment.webUrl",        MockDeploymentServer.getBaseUrl() + "/deployment/",
                "b-bot.apps.deployment.apiBase",       MockDeploymentServer.getBaseUrl()
            ));

        // 3. Build session — descriptor-class in application.conf drives auto-discovery.
        //    No explicit .register() calls needed; BBotSession.Builder.initialize() reads
        //    b-bot.apps.*.descriptor-class and instantiates each via Class.forName().
        BBotSession session = BBotSession.builder()
                .initialize(cfg)
                .build();
        BBotRegistry.setSession(session);

        // 4. Launch Playwright browser.
        PlaywrightManager.initBrowser();
    }

    @Before
    public void openFreshContext() { PlaywrightManager.initContext(); }

    @After
    public void closeContext()     { PlaywrightManager.closeContext(); }

    @AfterAll
    public static void shutdownBrowser() {
        PlaywrightManager.closeBrowser();
        HttpClientFactory.shutdown();
        BBotRegistry.clearSession();
        MockBlotterServer.stop();
        MockDeploymentServer.stop();
        MockConfigServer.stop();
    }
}
```

---

### Revised Step Definitions

```java
public class BondBlotterSteps {

    private final BlotterDsl blotter;

    // PicoContainer calls this AFTER @Before — page is already ready
    public BondBlotterSteps() {
        this.blotter = BBotRegistry.dsl("blotter", PlaywrightManager.getPage(), BlotterDsl.class);
    }

    @Given("the PT-Blotter is open")
    public void openBlotter()              { blotter.openBlotter(); }

    @Given("the PT-Blotter is open as user {string}")
    public void openBlotterAs(String user) { blotter.openBlotter(user); }
    // All other steps: unchanged — they delegate to blotter.*
}

public class ConfigServiceSteps {

    private final ConfigServiceDsl configService;

    public ConfigServiceSteps() {
        // page = null — REST-only descriptor ignores it
        this.configService = BBotRegistry.dsl("config-service", null, ConfigServiceDsl.class);
    }
    // ...
}
```

---

### Generic Precondition Steps (enabled by descriptors)

```java
// stepdefs/AppPreconditionSteps.java  (sandbox; ~15 lines; can be copied to any consumer)
public class AppPreconditionSteps {

    @Given("the {string} app is healthy")
    public void appIsHealthy(String appName) {
        BBotRegistry.checkHealth(appName);
    }

    @Given("the {string} service is running at version {string}")
    public void serviceAtVersion(String appName, String version) {
        BBotRegistry.assertVersion(appName, version);
    }

    @Given("all registered apps are healthy")
    public void allAppsHealthy() {
        BBotRegistry.checkAllHealth();
    }
}
```

These three steps cover **any component** registered with the framework — no
app-specific step code required for health or version assertions ever again:

```gherkin
@precondition
Scenario: Credit RFQ stack is live at expected versions
  Given the "blotter" app is healthy
  And the "config-service" app is healthy
  And the "blotter" service is running at version "v2.4.1"
```

---

### Real Consumer: `pt-blotter-regression-template` (delivered in M5)

```java
// pt-blotter-regression-template/src/test/java/stepdefs/Hooks.java
// Zero mock servers — all URLs come from application-{env}.conf
// Zero .register() calls — descriptor-class in HOCON drives auto-discovery
public class Hooks {

    private static PlaywrightManager browser;

    @BeforeAll
    public static void launchBrowser() {
        BBotConfig cfg = BBotConfig.load();   // picks up application-{env}.conf automatically

        // BBotSession.Builder.initialize() reads b-bot.apps.*.descriptor-class
        // and instantiates each descriptor via Class.forName() — no .register() needed.
        BBotSession session = BBotSession.builder()
                .initialize(cfg)
                .build();
        BBotRegistry.setSession(session);

        browser = new PlaywrightManager(cfg);
        browser.initBrowser();
    }

    @Before  public void openFreshContext() { browser.initContext(); }
    @After   public void closeContext()     { browser.closeContext(); }
    @AfterAll public static void shutdownBrowser() {
        browser.closeBrowser();
        HttpClientFactory.shutdown();
        BBotRegistry.clearSession();
    }
}
```

```bash
mvn verify -Db-bot.env=uat       # UAT
mvn verify -Db-bot.env=preprod   # pre-prod
B_BOT_ENV=uat mvn verify         # same via env var (CI-friendly)
```

---

## Approach 3 — JUnit 5 Extension + SPI Convention Model

*(Kept as reference — explicitly deferred.)*

Consumers annotate their runner `@BBotTest(environment = UatEnvironment.class)` and
get full lifecycle wiring with zero Hooks boilerplate. Powerful, but carries real risk:
the JUnit 5 Extension lifecycle and Cucumber Platform Engine lifecycle conflict — a
class of bug we have already encountered in this codebase (`System.exit()`, Surefire
discovery ordering). Layer this on top of the descriptor architecture once the API
has stabilised across at least two real consumer projects.

---

## Comparison Matrix

| Criterion | Approach 1 (Properties) | Approach 2 + Descriptors (Recommended) | Approach 3 (Extension/SPI) |
|-----------|------------------------|----------------------------------------|---------------------------|
| Config expressiveness | Low (flat) | High (HOCON, hierarchical, typed) | High (plain Java) |
| Multi-env support | Manual copy/paste | First-class (`include` + override) | First-class (`@env` tag) |
| Secret injection | Manual env-var reading | Native `${?B_BOT_VAR}` syntax | Team's choice |
| App decoupling | Partial (URL injection only) | Full (descriptor is a factory; health/version paths in HOCON) | Full |
| Generic health/version checks | No | Yes — via HOCON `health-check-path` / `version-path` | Yes |
| New consumer boilerplate | Medium | Very low (descriptor per app + zero-line Hooks; HOCON drives auto-discovery) | Very low (1 annotation) |
| Step defs reference apps by | Class name | Logical string name ("blotter") | Logical string name |
| Extensibility | Low | Medium (new descriptor = new app) | High (SPI plugins) |
| New dependencies in core | None | `typesafe:config` (180 KB, MIT) | None |
| Debuggability | High | High | Low (multi-layer magic) |
| Risk of over-engineering | Low | Low–Medium | High |

---

## Open Questions

| # | Question | Recommendation |
|---|----------|----------------|
| 1 | Export `AppPreconditionSteps` with Cucumber annotations from core? | No — keeps core Cucumber-free; copy the 15-line class into each consumer |
| 2 | Cross-app URL access (`blotter` needs `config-service`'s apiBase) | First-class `ctx.getOtherAppApiBase(name)` accessor — config read, not class coupling |
| 3 | Playwright `compile` scope bleeds into consumer's compile classpath | Acceptable and expected; document in core README |
| 4 | `bundle.js` still manually synced with 6 source probe modules | M1: add `exec-maven-plugin` phase that concatenates the 6 modules into `bundle.js` |
| 5 | `webUrl` always ends with `/`; `apiBase` never does — enforce? | Document in `AppDescriptor` Javadoc; validate in `AppContext` constructor (throw on violation) |
| 6 | Secrets (Vault, AWS Secrets Manager) | `BBotConfig.load()` accepts an optional `Map<String,String> secrets` override in M6 |

---

---

# Milestone Plan — Quality-Gated Delivery

Each milestone builds on the previous one. The invariant is simple:
**66/66 tests must pass at the end of every milestone without exception.**
No milestone is "done" until all its quality gates are green and a clean git commit is made.

**Status: M1–M5 COMPLETE. 66/66 passing. `pt-blotter-regression-template` module shipped.**

---

## M1 — Module Separation ✅ DONE

**Goal:** Two Maven modules exist and compile. Core infrastructure files live in `b-bot-core/src/main/java`. All tests still pass. No logic changed — only package declarations and build configuration.

### Tasks

1. Create parent POM `b-bot/pom.xml`:
   - `<groupId>com.bbot</groupId>`, `<version>1.0.0-SNAPSHOT</version>`, `<packaging>pom</packaging>`
   - `<modules>` listing `b-bot-core` and `b-bot-sandbox`
   - `<dependencyManagement>` for all library versions (Playwright, Cucumber, JUnit 5, Jackson, AssertJ, WireMock)

2. Create `b-bot-core/pom.xml`:
   - Dependencies: `playwright` (compile), `jackson-databind` (compile), `assertj-core` (compile)
   - No WireMock, no Cucumber — core must not need them

3. Move to `b-bot-core/src/main/java/com/bbot/core/`:
   - `PlaywrightManager`, `ProbesLoader`, `GridHarness`, `TickingCellHelper`, `NumericComparator`
   - Update `package` declarations to `com.bbot.core`

4. Move JS probes to `b-bot-core/src/main/resources/js/probes/`:
   - `bundle.js` + all 6 source modules
   - Remove the old `<testResources>` probes entry from sandbox `pom.xml` (now on classpath via core JAR)
   - Add `exec-maven-plugin` execution in core: concatenate the 6 source modules into `bundle.js` during `generate-resources` (removes manual sync burden permanently)

5. Rename `b-autobot` → `b-bot-sandbox` in `pom.xml`; add `<dependency>` on `b-bot-core`

6. Fix all sandbox imports and package declarations: `package stepdefs;` → `package com.bbot.sandbox.stepdefs;` etc.; `utils.PlaywrightManager` → `com.bbot.core.PlaywrightManager` etc.

7. Add placeholder `reference.conf` to `b-bot-core/src/main/resources/` (content from M2; empty `b-bot {}` block is fine for now)

### Quality Gates

```bash
# G1.1 — Core compiles with zero Cucumber or sandbox references
mvn compile -pl b-bot-core
grep -rn "io.cucumber\|stepdefs\|MockBlotterServer\|MockConfigServer\|MockDeploymentServer" \
  b-bot-core/src/main/java/
# Must print: (no output)

# G1.2 — Core JAR contains bundle.js on the expected classpath
mvn package -pl b-bot-core -DskipTests
jar tf b-bot-core/target/b-bot-core-1.0.0-SNAPSHOT.jar | grep "js/probes/bundle.js"
# Must print exactly one line

# G1.3 — No circular dependency: core must NOT reference sandbox
mvn dependency:tree -pl b-bot-core | grep "b-bot-sandbox"
# Must print: (no output)

# G1.4 — Old import paths gone from sandbox step definitions
grep -rn "^import utils\.\(PlaywrightManager\|ProbesLoader\|GridHarness\|TickingCellHelper\|NumericComparator\)" \
  b-bot-sandbox/src/test/java/stepdefs/
# Must print: (no output)

# G1.5 — Full regression: definitive gate
mvn verify -pl b-bot-sandbox
# Must print: Tests run: 66, Failures: 0, Errors: 0

# G1.6 — Headed smoke: visual confirmation (manual, once)
mvn verify -pl b-bot-sandbox -DHEADLESS=false -Dcucumber.filter.tags="@smoke"
# Blotter must open in a real browser window and the grid must render
```

**Commit message:** `M1: module split — b-bot-core extracts PlaywrightManager, GridHarness, TickingCellHelper, ProbesLoader, NumericComparator + JS probes`

---

## M2 — Configuration Layer (`BBotConfig`) ✅ DONE

**Goal:** `BBotConfig` exists in core, loads `reference.conf` defaults, supports layered
environment overrides and `withOverrides()` for runtime URL injection. Unit-tested in
isolation — not yet wired into the sandbox `Hooks.java`.

### Tasks

1. Add `com.typesafe:config:1.4.3` to `b-bot-core/pom.xml` as `compile` scope

2. Implement `com.bbot.core.config.BBotConfig`:
   - `BBotConfig.load()` static factory — reads `b-bot.env` system property or `B_BOT_ENV` env var
   - Layered loading: system props > `application-{env}.conf` > `application.conf` > `reference.conf`
   - `withOverrides(Map<String, String>)` — immutable, returns new instance
   - Typed accessors: `getString`, `getBoolean`, `getTimeout`, `hasPath`
   - App-specific accessors: `getAppWebUrl(name)`, `getAppApiBase(name)`, `getAppUsers(name)`, `getAppVersions(name)`

3. Populate `reference.conf` with full browser and timeout defaults under `b-bot.*`

4. Add `application.conf` to `b-bot-sandbox/src/test/resources/` with user entries only (no URLs — those come at runtime)

5. Write unit tests in `b-bot-core/src/test/java/com/bbot/core/config/BBotConfigTest.java`:
   - `loadDefaults()` — `reference.conf` values resolve correctly
   - `withOverridesWins()` — programmatic override beats HOCON value
   - `envLayerOverridesBase()` — `application-uat.conf` (test resource) overrides `application.conf`
   - `getAppWebUrlMissing()` — returns null when key absent (no exception)
   - `getAppUsersEmpty()` — returns empty map when block absent
   - `systemPropertyWins()` — `-Db-bot.apps.blotter.webUrl=X` beats conf file value

### Quality Gates

```bash
# G2.1 — Core unit tests pass (no browser, no Playwright)
mvn test -pl b-bot-core
# Must print: Tests run: 6+, Failures: 0

# G2.2 — reference.conf is inside the core JAR
mvn package -pl b-bot-core -DskipTests
jar tf b-bot-core/target/b-bot-core-1.0.0-SNAPSHOT.jar | grep "reference.conf"
# Must print exactly one line

# G2.3 — typesafe:config appears as transitive dep in sandbox (not re-declared)
mvn dependency:tree -pl b-bot-sandbox | grep "typesafe"
# Must show the dependency (transitively); must not appear twice

# G2.4 — Full regression unchanged (BBotConfig exists but Hooks still uses old pattern)
mvn verify -pl b-bot-sandbox
# Must print: Tests run: 66, Failures: 0, Errors: 0

# G2.5 — Environment activation works end-to-end (unit test verifies selection logic;
#         or run with an env value and confirm no crash)
mvn verify -pl b-bot-core -Db-bot.env=someenv-that-does-not-exist 2>&1 | grep "Missing.*application-someenv"
# Config must fail gracefully with a clear message when env file is missing
```

**Commit message:** `M2: BBotConfig — HOCON layered config under b-bot.* namespace; reference.conf defaults; unit tested`

---

## M3 — Registry + Descriptor Interfaces ✅ DONE

**Goal:** `AppDescriptor`, `AppContext`, `DslFactory`, `ComponentType`, `BBotRegistry` exist in core
and are fully unit-tested. Not yet wired into the sandbox — the existing Hooks, DSLs, and step defs are
untouched. 66/66 still passes.

### Tasks

1. Implement in `b-bot-core/src/main/java/com/bbot/core/registry/`:
   - `ComponentType.java` (enum)
   - `DslFactory.java` (functional interface)
   - `AppDescriptor.java` (interface, two optional defaults)
   - `AppContext.java` (value object; `fromConfig(name, BBotConfig)` static factory that reads `b-bot.apps.{name}.*`)
   - `BBotRegistry.java` (static; `register`, `initialize`, `dsl`, `checkHealth`, `assertVersion`, `checkAllHealth`, `reset`)

2. Write unit tests in `b-bot-core/src/test/java/com/bbot/core/registry/`:
   - `BBotRegistryTest`:
     - `registerAndDslCallsFactory()` — mock descriptor, verify factory is called with correct args
     - `unknownAppNameThrows()` — `dsl("nonexistent", ...)` → `IllegalArgumentException`
     - `dslBeforeInitializeThrows()` — `dsl()` called before `initialize()` → `IllegalStateException`
     - `resetClearsAll()` — after `reset()`, `dsl()` throws as if never initialised
     - `checkHealthCallsEndpoint()` — use WireMock or a stub HTTP server to assert the health URL is called
     - `checkHealthNoOpWhenNoPath()` — descriptor with empty `healthCheckPath()` does not make HTTP call
   - `AppContextTest`:
     - `fromConfigReadsWebUrl()`, `fromConfigReadsApiBase()`, `fromConfigReadsUsers()`
     - `fromConfigEmptyWhenKeyMissing()` — no exception for absent optional keys
   - `AppDescriptorDefaultsTest`:
     - `healthCheckPathIsEmptyByDefault()`, `versionPathIsEmptyByDefault()`

### Quality Gates

```bash
# G3.1 — All registry + descriptor unit tests pass
mvn test -pl b-bot-core
# Must print: Tests run: 15+, Failures: 0

# G3.2 — No Cucumber dependency anywhere in core
mvn dependency:tree -pl b-bot-core | grep "cucumber"
# Must print: (no output)

# G3.3 — No sandbox-specific classes imported in core
grep -rn "MockBlotterServer\|MockConfigServer\|MockDeploymentServer\|BlotterDsl\|ConfigServiceDsl\|DeploymentDsl" \
  b-bot-core/src/
# Must print: (no output)

# G3.4 — Full regression untouched (registry exists but not wired in sandbox yet)
mvn verify -pl b-bot-sandbox
# Must print: Tests run: 66, Failures: 0, Errors: 0

# G3.5 — Core JAR size sanity check
ls -lh b-bot-core/target/b-bot-core-1.0.0-SNAPSHOT.jar
# Must be well under 5 MB (large JAR = bundling something unexpected)
```

**Commit message:** `M3: AppDescriptor / AppContext / BBotRegistry — descriptor-driven component registry; fully unit tested`

---

## M4 — DSL Decoupling + End-to-End Wiring ✅ DONE

**Goal:** Sever every static mock-server call from every DSL. Wire `BBotRegistry` into
`Hooks.java` and step definitions. 66/66 must pass **after every sub-step** — do not
accumulate changes and fix at the end.

Work in strictly ordered sub-steps, committing after each one passes 66/66.

### Sub-step 4a — Descriptor implementations

Create `b-bot-sandbox/src/test/java/descriptors/`:
- `BlotterAppDescriptor.java`, `ConfigServiceDescriptor.java`, `DeploymentDescriptor.java`

Descriptors exist but `Hooks.java` does not use them yet.

```bash
mvn verify -pl b-bot-sandbox   # Must be: 66/66 — descriptors are inert at this point
```

**Commit:** `M4a: BlotterAppDescriptor, ConfigServiceDescriptor, DeploymentDescriptor — registered but not yet wired`

### Sub-step 4b — Decouple `ConfigServiceDsl`

1. Change constructor: `ConfigServiceDsl(String baseUrl)` — remove `MockConfigServer.getBaseUrl()` inside the class
2. Update `ConfigServiceSteps` to get its DSL via `BBotRegistry.dsl("config-service", null, ConfigServiceDsl.class)`
3. Add `ConfigServiceDescriptor` to `Hooks.java` + inject `config-service.apiBase` override

```bash
mvn verify -Dcucumber.filter.tags="@config-service"   # 14/14 first
mvn verify -pl b-bot-sandbox                           # then 66/66
```

**Commit:** `M4b: ConfigServiceDsl decoupled — baseUrl injected via AppContext`

### Sub-step 4c — Decouple `DeploymentDsl`

1. Change constructor: `DeploymentDsl(Page page, String apiBaseUrl)` — remove `MockDeploymentServer.getBaseUrl()`
2. Update `DeploymentSteps`; add `DeploymentDescriptor` to `Hooks.java` + inject deployment URL overrides

```bash
mvn verify -Dcucumber.filter.tags="@deployment"   # 15/15 first
mvn verify -pl b-bot-sandbox                       # then 66/66
```

**Commit:** `M4c: DeploymentDsl decoupled — apiBaseUrl injected via AppContext`

### Sub-step 4d — Decouple `BlotterDsl` (hardest — references two servers)

1. Change constructor: `BlotterDsl(Page page, AppContext ctx)`
2. Replace `MockBlotterServer.getBlotterUrl()` → `ctx.getWebUrl()`
3. Replace `MockConfigServer.getBaseUrl()` → `ctx.getOtherAppApiBase("config-service")`
4. Update `BondBlotterSteps`; add `BlotterAppDescriptor` to `Hooks.java` + inject blotter URL overrides

```bash
mvn verify -Dcucumber.filter.tags="@blotter"   # 39/39 first
mvn verify -pl b-bot-sandbox                   # then 66/66
```

**Commit:** `M4d: BlotterDsl decoupled — AppContext injection; config-service URL via getOtherAppApiBase`

### Sub-step 4e — Complete `Hooks.java` rewrite

Replace the old ad-hoc Hooks with the full `BBotConfig.load().withOverrides(...) → BBotRegistry.initialize(cfg)` pattern as shown in the design above.

```bash
mvn verify -pl b-bot-sandbox   # 66/66 — the definitive gate for this sub-step
```

**Commit:** `M4e: Hooks.java rewritten — BBotConfig + BBotRegistry lifecycle, mock-server port injection via withOverrides`

### Sub-step 4f — Add `AppPreconditionSteps`

Create `stepdefs/AppPreconditionSteps.java` with the three generic steps.
Verify the existing `@precondition` scenario still passes.

```bash
mvn verify -Dcucumber.filter.tags="@precondition"   # 1/1
mvn verify -pl b-bot-sandbox                         # 66/66
```

**Commit:** `M4f: AppPreconditionSteps — generic health + version assertions via BBotRegistry`

### Milestone-Level Quality Gates

```bash
# G4.1 — THE gate: zero static mock-server calls inside DSL files
grep -n "MockBlotterServer\|MockConfigServer\|MockDeploymentServer" \
  b-bot-sandbox/src/test/java/utils/BlotterDsl.java \
  b-bot-sandbox/src/test/java/utils/ConfigServiceDsl.java \
  b-bot-sandbox/src/test/java/utils/DeploymentDsl.java
# Must print: (no output)

# G4.2 — DSL files import only from com.bbot.core, playwright, or JDK
grep "^import" b-bot-sandbox/src/test/java/utils/BlotterDsl.java | \
  grep -v "com\.bbot\.core\|com\.microsoft\.playwright\|java\.\|com\.fasterxml"
# Must print: (no output)

# G4.3 — No old-style bare DSL constructors in step definitions
grep -n "new BlotterDsl(page)\|new ConfigServiceDsl()\|new DeploymentDsl(page)" \
  b-bot-sandbox/src/test/java/stepdefs/
# Must print: (no output)

# G4.4 — Full regression (non-negotiable)
mvn verify -pl b-bot-sandbox
# Must print: Tests run: 66, Failures: 0, Errors: 0

# G4.5 — Headed visual check (manual, once per milestone)
mvn verify -pl b-bot-sandbox -DHEADLESS=false -Dcucumber.filter.tags="@smoke"
# Blotter opens in Chromium window, grid renders, smoke scenario passes visually

# G4.6 — Environment variable path (smoke-level)
B_BOT_ENV=local mvn verify -pl b-bot-sandbox -Dcucumber.filter.tags="@smoke"
# Must pass (same as default; confirms env-var code path is exercised)
```

**Commit message:** `M4: DSL decoupling complete — AppContext injection end-to-end; BBotRegistry wired; 66/66`

---

## M5 — Core Published + Consumer Template ✅ DONE

**Goal:** `b-bot-core` is a standalone, installable JAR. `pt-blotter-regression-template` is a
fully-documented copy-adapt starter for real-system consumers, living as a peer Maven module
in the reactor. `mvn verify` passes 66/66 in the sandbox; the template smoke scenario passes
when BlotterDevServer is running.

### What was delivered

1. **`b-bot-core`** installed to local `.m2` — consumers can depend on it from any project.

2. **`pt-blotter-regression-template/`** — added to the reactor as a third Maven module:
   - `pom.xml` inherits parent; `skipTests=true` by default; activates `real-env` profile
     automatically when `-Db-bot.env=<anything>` is passed (no manual profile activation needed)
   - `Hooks.java` — no mock servers; `BBotConfig.load()` + register + initialize + initBrowser
   - `BlotterDescriptor.java`, `BlotterSteps.java`, `AppPreconditionSteps.java`, `PtBlotterDsl.java`
   - `application.conf` — all b-bot-core HOCON keys shown with defaults and comments
   - `application-devserver.conf` — points at `localhost:9099` (BlotterDevServer)
   - `features/Smoke.feature` — one `@smoke` scenario
   - `README.md` — developer quick-start guide (prerequisites, 3-step smoke run, how to adapt)

3. **Smoke test** against `BlotterDevServer`:
   ```bash
   # Terminal 1 — start mock blotter server
   mvn exec:java -pl b-bot-sandbox \
       -Dexec.mainClass=com.bbot.sandbox.utils.BlotterDevServer \
       -Dexec.classpathScope=test

   # Terminal 2 — run template smoke
   mvn verify -pl pt-blotter-regression-template -Db-bot.env=devserver
   # → Tests run: 1, Failures: 0
   ```

### Quality Gates

```bash
# G5.1 — Core JAR installable
mvn install -pl b-bot-core -DskipTests
ls ~/.m2/repository/com/bbot/b-bot-core/1.0.0-SNAPSHOT/b-bot-core-1.0.0-SNAPSHOT.jar
# File must exist

# G5.2 — Template compiles in reactor (skipTests by default)
mvn verify -pl pt-blotter-regression-template
# Must print: BUILD SUCCESS (0 tests run — skipTests=true)

# G5.3 — Smoke against DevServer
mvn verify -pl pt-blotter-regression-template -Db-bot.env=devserver
# Must print: Tests run: 1, Failures: 0

# G5.4 — Sandbox regression unaffected
mvn verify -pl b-bot-sandbox
# Must print: Tests run: 66, Failures: 0, Errors: 0

# G5.5 — Full reactor build clean
mvn verify
# Must print: BUILD SUCCESS across all modules
```

**Commit message:** `M5: b-bot-core installable; pt-blotter-regression-template module + README + devserver smoke`

---

## M6 — Real Consumer: UAT Smoke (VPN / Network-gated)

**Goal:** At least 3 scenarios pass against a live UAT environment with zero mock infrastructure.
Proof that the full architecture — descriptors, HOCON layering, health checks, version
assertions — works end-to-end against a real system.

### Tasks

1. Obtain UAT service URLs and any auth tokens from infra/ops team

2. Write `pt-blotter-regression-template/src/test/resources/application-uat.conf` with real values

3. Write (or adapt) `BlotterAppDescriptor` for any UAT-specific quirks (different URL query params, auth headers, etc.)

4. Implement 3 smoke scenarios:
   - `"the blotter app is healthy"` — health check against UAT `/api/health`
   - `"the blotter page loads with a grid"` — navigate, wait for `[row-index='0']`
   - `"the config-service app is healthy"` — health check; verify user permissions endpoint returns 200

5. Write `UAT_RUNBOOK.md` documenting: required network access, env vars, activation commands, known UAT limitations

### Quality Gates

```bash
# G6.1 — UAT smoke: 3 scenarios green against live system
cd pt-blotter-regression-template
B_BOT_ENV=uat mvn verify -Dcucumber.filter.tags="@smoke"
# Must print: Tests run: 3, Failures: 0, Errors: 0

# G6.2 — Health step works via BBotRegistry (inspect logs to confirm HTTP call was made)
B_BOT_ENV=uat mvn verify -Dcucumber.filter.tags="@precondition"
# Must print: Tests run: 1+, Failures: 0

# G6.3 — No mock-server classes anywhere in pt-blotter-regression-template
grep -r "MockBlotterServer\|MockConfigServer\|MockDeploymentServer\|WireMockServer\|wiremock" \
  pt-blotter-regression-template/src/
# Must print: (no output)

# G6.4 — Headed browser confirms real UAT blotter (manual, once)
B_BOT_ENV=uat mvn verify -Dcucumber.filter.tags="@smoke" -DHEADLESS=false
# Real UAT blotter visible in Chromium; grid renders with live or seed data

# G6.5 — Sandbox regression completely unaffected
mvn verify -pl b-bot-sandbox
# Must print: Tests run: 66, Failures: 0, Errors: 0
```

**Git tag on success:** `v1.0.0-validated` — marks the point at which the architecture
is proven against a real system and `b-bot-core` is ready for wider adoption.

---

## Milestone Summary

| Milestone | Focus | Critical gate | Sandbox regression |
|-----------|-------|---------------|--------------------|
| M1 | Module split — move files, fix packages | Core JAR contains `bundle.js`; no Cucumber in core | 66/66 |
| M2 | `BBotConfig` — HOCON loading, `withOverrides` | Unit tests pass; `reference.conf` in JAR | 66/66 |
| M3 | Registry + descriptor interfaces | Registry unit tests pass; zero Cucumber in core | 66/66 |
| M4 | DSL decoupling + end-to-end wiring | Zero static `MockServer` calls in DSL files | 66/66 |
| M5 | Publish core JAR; consumer skeleton compiles | Consumer smoke vs DevServer passes (1/1) | 66/66 |
| M6 | Real consumer: UAT smoke | 3 scenarios green against live UAT | 66/66 |

The 66/66 sandbox regression is a non-negotiable invariant at every milestone.
It is the safety net that proves the refactoring never breaks the reference implementation.

---

## Industrialisation Milestones (M7–M12)

After the modularisation was complete (M1–M6), a second phase of work raised the
codebase to industrial-grade library quality. These milestones are documented in
`IMPLEMENTATION_PLAN.md`; a brief summary is included here for completeness.

| Milestone | Focus | Outcome |
|-----------|-------|---------|
| M7 | Core unit test coverage | 184 unit tests, JaCoCo 65% threshold enforced |
| M8a–f | Typed exceptions + SLF4J logging + Playwright tracing | `BBotException` hierarchy (6 types); 8 classes instrumented |
| M9 | REST client hardening | `RestProbe` — GET/POST/PUT/DELETE/PATCH, auth, retry, shared `HttpClient` |
| M10 | Dependency inversion interfaces | `BrowserLifecycle`, `GridQuery`, `CellAssertions`, `RestClient` — mockable |
| M11 | Instance-based architecture | `BBotSession` + PicoContainer DI; static API `@Deprecated(forRemoval=true)` |
| M12 | CI + Javadoc + documentation | GitHub Actions 3-job pipeline; Javadoc JAR (0 warnings); MD sync |

### Key M11 additions to `b-bot-core`

```
b-bot-core/src/main/java/com/bbot/core/
├── BrowserLifecycle.java          ← interface for browser lifecycle
├── CellAssertions.java            ← interface for ticking-cell assertions
├── GridQuery.java                 ← interface for AG Grid row queries
├── exception/
│   ├── BBotException.java         ← base unchecked exception
│   ├── BBotConfigException.java
│   ├── BBotGridRowNotFoundException.java
│   ├── BBotHealthCheckException.java
│   ├── BBotRestException.java
│   └── BBotTemplateException.java
├── registry/
│   ├── BBotSession.java           ← NEW: immutable session (instance API)
│   └── BBotRegistry.java         ← CHANGED: static delegates + @Deprecated
└── rest/
    ├── RestClient.java            ← NEW: interface
    ├── ScenarioContext.java       ← NEW: per-scenario instance-based state
    ├── ScenarioState.java         ← CHANGED: delegates to thread-local ScenarioContext
    ├── AuthStrategy.java          ← NEW: bearer token / no-auth
    ├── RetryPolicy.java           ← NEW: exponential backoff record
    └── HttpClientFactory.java     ← NEW: shared HttpClient factory
```

### PicoContainer DI pattern (M11)

Both consuming modules (`b-bot-sandbox` and `pt-blotter-regression-template`) use
`cucumber-picocontainer` to inject a shared `TestWorld` into every step definition class.
PicoContainer creates a fresh `TestWorld` (and therefore a fresh `ScenarioContext`) for
each scenario automatically — no manual reset needed for the instance-based API.

```java
// TestWorld is created once per scenario by PicoContainer
public class TestWorld {
    private final BBotSession     session;         // immutable — safe to share
    private final ScenarioContext scenarioContext;  // fresh per scenario

    public TestWorld() {
        this.session         = BBotRegistry.session();
        this.scenarioContext = new ScenarioContext();
    }
}

// Step definitions receive the same TestWorld instance
public class BondBlotterSteps {
    public BondBlotterSteps(TestWorld world) {
        this.dsl = world.session().dsl("blotter", world.page(), BlotterDsl.class);
    }
}
```

---

## M13 — Enterprise SSO / MFA Authentication

### Summary
Added enterprise SSO authentication support to `b-bot-core`, enabling regression suites
to run against real UAT environments protected by Azure AD / ADFS / Ping Federate with
mandatory MFA.

### New Packages & Classes

| Class | Package | Role |
|-------|---------|------|
| `SsoAuthConfig` | `com.bbot.core.auth` | Immutable record — parses `b-bot.auth` HOCON block; validates per mode |
| `SsoAuthManager` | `com.bbot.core.auth` | Orchestrator — interactive login, storageState reuse, OAuth client_credentials |
| `ClientCredentialsAuth` | `com.bbot.core.auth` | `AuthStrategy` impl — OAuth2 token with auto-refresh |
| `StorageStateAuth` | `com.bbot.core.rest` | `AuthStrategy` impl — extracts cookies/tokens from Playwright storageState |
| `BBotAuthException` | `com.bbot.core.exception` | Typed exception for auth failures with actionable hints |

### Config Schema (`reference.conf`)
```hocon
b-bot.auth {
  mode = none                         # none | interactive | storageState | auto | clientCredentials
  storageStatePath = "target/auth/storage-state.json"
  sessionTtl = 4h
  loginUrl = ""
  loginTimeout = 120s
  loginSuccessIndicator = "pause"     # pause | urlContains:<pattern> | element:<selector>
  tokenUrl = ""
  clientId = ""
  clientSecret = ""
  scope = ""
  refreshOn401 = true
}
```

### Integration Points
- `PlaywrightManager.initContext()` — injects storageState into new browser contexts
- `RestProbe.execute()` — 401/403 with active auth throws `BBotAuthException`
- `AuthStrategy.fromStorageState(Path)` — factory for REST auth from storageState
- Template `Hooks.java` — calls `SsoAuthManager.ensureAuthenticated()` before browser launch

### Test Coverage
252 unit tests, including 57 new auth-related tests across 4 test classes:
- `SsoAuthConfigTest` (32 tests)
- `SsoAuthManagerTest` (17 tests)
- `ClientCredentialsAuthTest` (7 tests)
- `StorageStateAuthTest` (7 tests)

### Quality Gates Passed
- JaCoCo ≥ 65% ✅
- Javadoc 0 warnings ✅
- All 252 tests green ✅
- Template compiles with SSO wiring ✅

