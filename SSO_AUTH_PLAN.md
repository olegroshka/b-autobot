# Enterprise SSO / MFA Authentication — Implementation Plan

## Problem Statement

In real enterprise environments (UBS, Goldman Sachs, JPMorgan, etc.), every web
application sits behind a corporate SSO gateway — typically **Azure AD / ADFS /
Ping Federate** — with mandatory **multi-factor authentication** (MS Authenticator
push, TOTP, hardware token). The current b-bot-core `PlaywrightManager` creates a
**blank** `BrowserContext` for every scenario: no cookies, no session, no auth state.
This works against mock servers and internal dev tools, but **dies instantly** when
the browser hits a real SSO redirect because:

1. The SSO login page requires username + password.
2. An MFA challenge fires (e.g. MS Authenticator push notification).
3. The test framework has no way to pause, wait for human approval, and continue.
4. Even if login succeeds once, the session is discarded after each scenario.

The same problem affects **REST API calls** — corporate APIs often require
Bearer tokens that are obtained via the same OAuth/OIDC flow.

## Solution Overview

Playwright provides first-class support for **persisting and restoring browser
authentication state** via `BrowserContext.storageState()` and
`Browser.NewContextOptions.setStorageStatePath()`. The approach:

```
┌─────────────────────────────────────────────────────────┐
│                    ONCE (manual / @BeforeAll)            │
│                                                         │
│  1. Launch headed browser (headless=false)               │
│  2. Navigate to SSO login URL                            │
│  3. PAUSE — user logs in + approves MFA                  │
│  4. Detect post-login success (URL pattern / element)    │
│  5. context.storageState(path) → save cookies to JSON    │
│                                                         │
├─────────────────────────────────────────────────────────┤
│              EVERY SCENARIO (automated)                  │
│                                                         │
│  6. newContext(opts.setStorageStatePath(saved.json))      │
│  7. Navigate directly to app — SSO cookie is present     │
│  8. No login redirect, no MFA — session is pre-auth'd    │
│                                                         │
├─────────────────────────────────────────────────────────┤
│              REST CALLS (automated)                      │
│                                                         │
│  9. Parse storageState JSON → extract auth cookies/token │
│ 10. Inject as Cookie/Authorization header in RestProbe   │
│                                                         │
├─────────────────────────────────────────────────────────┤
│              CI PIPELINE (no human)                      │
│                                                         │
│ 11. OAuth client-credentials flow (service principal)    │
│     OR pre-seeded refresh token from CI secret           │
│ 12. No browser needed for token acquisition              │
└─────────────────────────────────────────────────────────┘
```

## Authentication Modes

| Mode | Config Value | Use Case | Human Required? |
|------|-------------|----------|-----------------|
| **none** | `b-bot.auth.mode = none` | Mock environments, internal tools | No |
| **interactive** | `b-bot.auth.mode = interactive` | Dev machine first run — user logs in via real browser | Yes (once) |
| **storageState** | `b-bot.auth.mode = storageState` | Dev machine subsequent runs — reuse cached session | No |
| **auto** | `b-bot.auth.mode = auto` | Try storageState first; if expired, fall back to interactive | Yes (only when session expires) |
| **clientCredentials** | `b-bot.auth.mode = clientCredentials` | CI pipelines — OAuth2 client credentials grant | No |

---

## Milestone Plan

### M13a — Config Schema + `SsoAuthConfig` Record ✅ COMPLETE
### M13b — `SsoAuthManager` Core Logic ✅ COMPLETE
### M13c — Wire into `PlaywrightManager` + `BrowserLifecycle` ✅ COMPLETE
### M13d — `AuthStrategy.fromStorageState()` for REST Calls ✅ COMPLETE
### M13e — `ClientCredentialsAuth` for CI Pipelines ✅ COMPLETE
### M13f — Staleness Detection + Auto-Refresh ✅ COMPLETE
### M13g — Cucumber Steps + Template Integration ✅ COMPLETE
### M13h — Unit Tests + Documentation ✅ COMPLETE

---

## M13a — Config Schema + `SsoAuthConfig` Record

### Goal
Define the HOCON config schema and a type-safe record that holds all auth settings.

### Config Addition to `reference.conf`

```hocon
b-bot {
  # ── Authentication ──────────────────────────────────────────────────────
  # Controls how the browser and REST client authenticate against SSO.
  #
  # Modes:
  #   none              — no authentication (default, for mock environments)
  #   interactive       — opens a headed browser, pauses for manual SSO login + MFA
  #   storageState      — loads a previously saved Playwright storageState JSON file
  #   auto              — tries storageState first; falls back to interactive if expired
  #   clientCredentials — OAuth2 client_credentials grant (for CI, no browser needed)
  auth {
    mode = none

    # ── Storage State (interactive / storageState / auto modes) ──────────
    # Path to the JSON file where browser cookies + localStorage are persisted.
    # Created by interactive mode; consumed by storageState / auto modes.
    storageStatePath = "target/auth/storage-state.json"

    # How long a cached storageState file is considered valid before re-login.
    # Enterprise SSO tokens typically last 1–8 hours.
    sessionTtl = 4h

    # ── Interactive Mode Settings ────────────────────────────────────────
    # URL that triggers the SSO redirect (usually the app's home page).
    loginUrl = ""

    # Maximum time to wait for the user to complete SSO login + MFA approval.
    loginTimeout = 120s

    # How to detect that login succeeded. Options:
    #   urlContains:<pattern>  — post-login URL contains this substring
    #   element:<selector>     — a CSS selector visible only after login
    #   pause                  — use Playwright Inspector pause (user clicks Resume)
    loginSuccessIndicator = "pause"

    # ── Client Credentials Mode (CI) ─────────────────────────────────────
    # OAuth2 token endpoint URL.
    tokenUrl = ""

    # Client ID for the service principal / app registration.
    # Can use HOCON env-var substitution: ${?B_BOT_CLIENT_ID}
    clientId = ""

    # Client secret — NEVER commit this; use env var or CI secret.
    # Can use HOCON env-var substitution: ${?B_BOT_CLIENT_SECRET}
    clientSecret = ""

    # OAuth2 scope(s), space-separated.
    scope = ""
  }
}
```

### New File: `SsoAuthConfig.java`

```
b-bot-core/src/main/java/com/bbot/core/auth/SsoAuthConfig.java
```

A record (or immutable POJO) that parses the `b-bot.auth` block from `BBotConfig`:

```java
public record SsoAuthConfig(
    AuthMode mode,
    Path     storageStatePath,
    Duration sessionTtl,
    String   loginUrl,
    Duration loginTimeout,
    String   loginSuccessIndicator,
    String   tokenUrl,
    String   clientId,
    String   clientSecret,
    String   scope
) {
    public enum AuthMode { NONE, INTERACTIVE, STORAGE_STATE, AUTO, CLIENT_CREDENTIALS }

    public static SsoAuthConfig from(BBotConfig cfg) { ... }

    public boolean isStorageStateValid() {
        // Check if file exists AND is younger than sessionTtl
    }
}
```

### New File: `BBotAuthException.java`

```
b-bot-core/src/main/java/com/bbot/core/exception/BBotAuthException.java
```

Extends `BBotException`. Thrown on auth failures (expired session, missing config,
OAuth token request failure, login timeout).

### Files Modified

| File | Change |
|------|--------|
| `b-bot-core/src/main/resources/reference.conf` | Add `b-bot.auth` block |
| `BBotConfig.java` | Add `getSsoAuthConfig()` accessor |

### Quality Gate — G13a

```bash
# G13a.1 — Config parses without error
mvn test -pl b-bot-core   # BBotConfigTest reads auth defaults

# G13a.2 — SsoAuthConfig record is complete
# SsoAuthConfigTest: all modes parse, env-var substitution works,
# isStorageStateValid() returns false when file missing

# G13a.3 — BBotAuthException fits the hierarchy
# extends BBotException, has message + cause constructors

# G13a.4 — All existing tests pass (zero regression)
mvn verify -pl b-bot-core,b-bot-sandbox
```

---

## M13b — `SsoAuthManager` Core Logic

### Goal
Implement the orchestrator that handles all five auth modes.

### New File: `SsoAuthManager.java`

```
b-bot-core/src/main/java/com/bbot/core/auth/SsoAuthManager.java
```

### Responsibilities

```java
public final class SsoAuthManager {

    /**
     * Ensures a valid storageState file exists on disk.
     * Called once in @BeforeAll, BEFORE initContext().
     *
     * Behaviour per mode:
     *   NONE              → no-op
     *   INTERACTIVE       → always run interactive login
     *   STORAGE_STATE     → validate file exists; throw if missing/expired
     *   AUTO              → if valid storageState exists, use it; else interactive
     *   CLIENT_CREDENTIALS → acquire OAuth token, synthesise storageState JSON
     */
    public static void ensureAuthenticated(SsoAuthConfig authConfig) { ... }

    /**
     * Returns the Path to the storageState JSON file, or null if mode=NONE.
     * Called by initContext() to inject into newContext().
     */
    public static Path getStorageStatePath(SsoAuthConfig authConfig) { ... }
}
```

### Interactive Login Flow (detail)

```java
private static void performInteractiveLogin(SsoAuthConfig config) {
    LOG.info("SSO interactive login — opening headed browser...");
    LOG.info("Navigate to: {}", config.loginUrl());
    LOG.info("Complete SSO login + MFA. Timeout: {}", config.loginTimeout());

    try (Playwright pw = Playwright.create()) {
        Browser browser = pw.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(false));
        BrowserContext ctx = browser.newContext();
        Page page = ctx.newPage();

        page.navigate(config.loginUrl());

        String indicator = config.loginSuccessIndicator();
        if ("pause".equals(indicator)) {
            // Opens Playwright Inspector — user clicks "Resume" after login
            page.pause();
        } else if (indicator.startsWith("urlContains:")) {
            String pattern = indicator.substring("urlContains:".length());
            page.waitForURL("**" + pattern + "**",
                new Page.WaitForURLOptions().setTimeout(config.loginTimeout().toMillis()));
        } else if (indicator.startsWith("element:")) {
            String selector = indicator.substring("element:".length());
            page.waitForSelector(selector,
                new Page.WaitForSelectorOptions().setTimeout(config.loginTimeout().toMillis()));
        }

        // Save cookies + localStorage
        Files.createDirectories(config.storageStatePath().getParent());
        ctx.storageState(new BrowserContext.StorageStateOptions()
            .setPath(config.storageStatePath()));

        LOG.info("SSO session saved to: {}", config.storageStatePath());
        browser.close();
    }
}
```

### Client Credentials Flow (detail)

```java
private static void performClientCredentialsGrant(SsoAuthConfig config) {
    LOG.info("OAuth2 client_credentials grant — tokenUrl: {}", config.tokenUrl());

    String body = "grant_type=client_credentials"
        + "&client_id=" + URLEncoder.encode(config.clientId(), UTF_8)
        + "&client_secret=" + URLEncoder.encode(config.clientSecret(), UTF_8)
        + "&scope=" + URLEncoder.encode(config.scope(), UTF_8);

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(config.tokenUrl()))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> resp = HttpClient.newHttpClient()
        .send(req, HttpResponse.BodyHandlers.ofString());

    if (resp.statusCode() != 200)
        throw new BBotAuthException("OAuth token request failed: " + resp.statusCode()
            + "\n" + resp.body());

    String accessToken = parseAccessToken(resp.body());

    // Synthesise a minimal storageState JSON with the token as a cookie
    // OR save the raw token for AuthStrategy.bearer() to use
    saveTokenState(config.storageStatePath(), accessToken);
    LOG.info("OAuth token acquired and saved to: {}", config.storageStatePath());
}
```

### Quality Gate — G13b

```bash
# G13b.1 — SsoAuthManager compiles, unit-testable
# Mock tests: NONE mode is no-op, STORAGE_STATE throws on missing file,
# CLIENT_CREDENTIALS parses a mocked token response

# G13b.2 — Interactive mode tested manually (cannot unit-test MFA)
# Manual verification: run with -Db-bot.auth.mode=interactive, complete SSO,
# verify storage-state.json is created

# G13b.3 — All existing tests pass
mvn verify -pl b-bot-core,b-bot-sandbox
```

---

## M13c — Wire into `PlaywrightManager` + `BrowserLifecycle`

### Goal
Modify the browser lifecycle so that `initContext()` loads the saved
storageState into every new `BrowserContext`.

### Changes to `PlaywrightManager.initContext()`

```java
// BEFORE creating the context:
SsoAuthConfig authConfig = SsoAuthConfig.from(cfg);
Path storagePath = SsoAuthManager.getStorageStatePath(authConfig);

Browser.NewContextOptions opts = new Browser.NewContextOptions()
        .setViewportSize(viewportW, viewportH);

// Inject saved auth state if available
if (storagePath != null && Files.exists(storagePath)) {
    opts.setStorageStatePath(storagePath);
    LOG.info("Browser context created with SSO storageState: {}", storagePath);
}

BrowserContext ctx = BROWSER.get().newContext(opts);
```

### Changes to `Hooks.java` (template)

```java
@BeforeAll
public static void launchBrowser() {
    BBotConfig cfg = BBotConfig.load();
    // ... register descriptors, initialize ...

    // NEW: ensure SSO auth before launching browser contexts
    SsoAuthConfig authConfig = SsoAuthConfig.from(cfg);
    SsoAuthManager.ensureAuthenticated(authConfig);

    PlaywrightManager.initBrowser();
}
```

### Changes to `BrowserLifecycle` Interface

No change needed — the `initContext()` contract is unchanged. The auth state
injection is an internal implementation detail of `PlaywrightManager`.

### New Scenario Context Flow

```
@BeforeAll:
  1. BBotConfig.load()
  2. BBotRegistry.initialize(cfg)
  3. SsoAuthManager.ensureAuthenticated(cfg)    ← NEW
  4. PlaywrightManager.initBrowser()

@Before (each scenario):
  5. PlaywrightManager.initContext()             ← now loads storageState
  6. page.navigate(appUrl)                       ← no SSO redirect!

@After:
  7. PlaywrightManager.closeContext()             ← session preserved on disk
```

### Quality Gate — G13c

```bash
# G13c.1 — mode=none is zero-change (backward compatible)
mvn verify -pl b-bot-sandbox   # 66/66 — nothing changes for mock env

# G13c.2 — mode=storageState injects path into context
# Unit test: verify newContext() receives storage state options

# G13c.3 — Template compiles with updated Hooks.java
mvn test-compile -pl pt-blotter-regression-template
```

---

## M13d — `AuthStrategy.fromStorageState()` for REST Calls

### Goal
REST API calls (via `RestProbe`) also need to authenticate. Extract auth
cookies/tokens from the saved storageState JSON and inject them into HTTP
request headers.

### New `AuthStrategy` Factory Method

```java
// In AuthStrategy.java:

/**
 * Creates an AuthStrategy that extracts cookies from a Playwright
 * storageState JSON file and injects them as Cookie headers.
 *
 * <p>For OAuth flows that produce a Bearer token, the token is also
 * injected as an Authorization header.
 */
static AuthStrategy fromStorageState(Path storageStatePath) {
    return new StorageStateAuth(storageStatePath);
}
```

### New File: `StorageStateAuth.java`

```
b-bot-core/src/main/java/com/bbot/core/rest/StorageStateAuth.java
```

```java
final class StorageStateAuth implements AuthStrategy {
    private final List<HttpCookie> cookies;
    private final String           bearerToken;   // nullable

    StorageStateAuth(Path storageStatePath) {
        // Parse the Playwright storageState JSON:
        // { "cookies": [...], "origins": [{ "localStorage": [...] }] }
        // Extract cookies relevant to the target domain.
        // If a cookie/localStorage entry looks like a Bearer token, capture it.
    }

    @Override
    public void apply(HttpRequest.Builder builder) {
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (!cookies.isEmpty()) {
            String cookieHeader = cookies.stream()
                .map(c -> c.getName() + "=" + c.getValue())
                .collect(Collectors.joining("; "));
            builder.header("Cookie", cookieHeader);
        }
    }
}
```

### Auto-Wiring in `RestProbe`

When `b-bot.auth.mode` is not `none`, the `RestProbe.of(apiBase)` factory
should auto-detect the configured auth mode and apply the appropriate
`AuthStrategy` — unless the caller has explicitly set one via the builder.

### Quality Gate — G13d

```bash
# G13d.1 — StorageStateAuth parses a sample storageState JSON
# Unit test with a fixture file containing cookies + token

# G13d.2 — Cookie header is correctly formatted
# G13d.3 — Bearer token extracted from localStorage entry

# G13d.4 — All existing tests pass (mode=none is default)
mvn verify -pl b-bot-core,b-bot-sandbox
```

---

## M13e — `ClientCredentialsAuth` for CI Pipelines

### Goal
Implement OAuth2 `client_credentials` grant for headless CI environments
where no human can approve MFA.

### New File: `ClientCredentialsAuth.java`

```
b-bot-core/src/main/java/com/bbot/core/auth/ClientCredentialsAuth.java
```

### Token Lifecycle

```java
public final class ClientCredentialsAuth implements AuthStrategy {

    private volatile String  accessToken;
    private volatile Instant expiresAt;

    public ClientCredentialsAuth(SsoAuthConfig config) {
        refreshToken(config);
    }

    @Override
    public void apply(HttpRequest.Builder builder) {
        if (Instant.now().isAfter(expiresAt.minus(Duration.ofMinutes(1)))) {
            refreshToken(config);   // proactive refresh before expiry
        }
        builder.header("Authorization", "Bearer " + accessToken);
    }

    private synchronized void refreshToken(SsoAuthConfig config) {
        // POST to tokenUrl with client_credentials grant
        // Parse JSON response: { "access_token": "...", "expires_in": 3600 }
    }
}
```

### CI Integration Pattern

```yaml
# GitHub Actions / Jenkins
env:
  B_BOT_CLIENT_ID: ${{ secrets.BBOT_CLIENT_ID }}
  B_BOT_CLIENT_SECRET: ${{ secrets.BBOT_CLIENT_SECRET }}

# application-ci.conf
b-bot.auth {
  mode = clientCredentials
  tokenUrl = "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token"
  clientId = ${?B_BOT_CLIENT_ID}
  clientSecret = ${?B_BOT_CLIENT_SECRET}
  scope = "api://blotter-api/.default"
}
```

### Quality Gate — G13e

```bash
# G13e.1 — ClientCredentialsAuth sends correct form body
# Unit test with a WireMock token endpoint

# G13e.2 — Token refresh fires when expires_in is near
# G13e.3 — BBotAuthException on 4xx/5xx from token endpoint

# G13e.4 — All existing tests pass
mvn verify -pl b-bot-core,b-bot-sandbox
```

---

## M13f — Staleness Detection + Auto-Refresh

### Goal
In `auto` mode, detect when the cached session has expired and transparently
re-trigger interactive login.

### Logic

```java
// In SsoAuthManager.ensureAuthenticated():
case AUTO:
    if (authConfig.isStorageStateValid()) {
        LOG.info("Cached SSO session is valid (age: {}). Reusing.",
                 formatAge(authConfig.storageStatePath()));
    } else {
        LOG.warn("Cached SSO session expired or missing. Starting interactive login...");
        performInteractiveLogin(authConfig);
    }
    break;
```

### Mid-Run Expiry Handling

For long test suites (> 1 hour), the session may expire mid-run. Detection
strategy:

1. **Browser side:** If a navigation triggers an SSO redirect (detected by URL
   pattern matching), throw `BBotAuthException` with a clear message:
   *"SSO session expired during scenario. Re-run with `-Db-bot.auth.mode=interactive`
   to re-authenticate."*

2. **REST side:** If `RestProbe` receives a 401/403, and auth mode is not `none`,
   throw `BBotAuthException` instead of the generic `BBotRestException`. The retry
   policy can optionally be configured to attempt one token refresh before failing.

### New Config Key

```hocon
b-bot.auth {
  # If true, 401 responses trigger a token refresh attempt before failing.
  # Only applies to clientCredentials mode (interactive cannot auto-refresh mid-run).
  refreshOn401 = true
}
```

### Quality Gate — G13f

```bash
# G13f.1 — isStorageStateValid() returns false for file older than sessionTtl
# G13f.2 — AUTO mode falls back to interactive when file is stale
# G13f.3 — 401 triggers BBotAuthException with actionable message
# G13f.4 — clientCredentials mode auto-refreshes on 401 (unit test with mock)

mvn verify -pl b-bot-core,b-bot-sandbox
```

---

## M13g — Cucumber Steps + Template Integration

### Goal
Provide ready-to-use Gherkin steps and a documented template pattern for
SSO-enabled regression suites.

### New Step Definitions (in template)

```gherkin
# Explicit auth step — use in @Background if you want it visible
Given I am authenticated via SSO

# Conditional — only runs interactive if session is expired
Given I have a valid SSO session
```

### Updated Template `Hooks.java`

```java
@BeforeAll
public static void launchBrowser() {
    BBotConfig cfg = BBotConfig.load();

    BBotRegistry.register(new BlotterDescriptor());
    BBotRegistry.initialize(cfg);

    // Ensure SSO session before browser launch
    SsoAuthConfig authConfig = SsoAuthConfig.from(cfg);
    SsoAuthManager.ensureAuthenticated(authConfig);

    PlaywrightManager.initBrowser();
}
```

### Template `application-uat.conf` Example

```hocon
# Real UAT environment with Azure AD SSO
b-bot {
  auth {
    mode = auto
    loginUrl = "https://blotter.ubs.com/"
    loginSuccessIndicator = "element:.ag-root-wrapper"
    sessionTtl = 4h
    storageStatePath = "target/auth/ubs-sso-state.json"
  }

  apps {
    blotter {
      webUrl  = "https://blotter.ubs.com/"
      apiBase = "https://blotter-api.ubs.com"
    }
  }
}
```

### Template `application-ci.conf` Example

```hocon
# CI pipeline with service principal
b-bot {
  auth {
    mode = clientCredentials
    tokenUrl = "https://login.microsoftonline.com/ubs-tenant/oauth2/v2.0/token"
    clientId = ${?B_BOT_CLIENT_ID}
    clientSecret = ${?B_BOT_CLIENT_SECRET}
    scope = "api://blotter-api/.default"
  }
}
```

### Quality Gate — G13g

```bash
# G13g.1 — Template compiles with updated Hooks.java
mvn test-compile -pl pt-blotter-regression-template

# G13g.2 — mode=none backward compatibility (sandbox unaffected)
mvn verify -pl b-bot-sandbox   # 66/66

# G13g.3 — Manual verification: run template against real UAT with mode=interactive
# Verify: browser opens, user logs in, storageState saved, scenarios run without redirect
```

---

## M13h — Unit Tests + Documentation

### Goal
Comprehensive test coverage for all testable paths (mock OAuth endpoints,
storageState parsing, config validation) and clear documentation.

### Test Matrix

| Test Class | Coverage |
|-----------|----------|
| `SsoAuthConfigTest` | All 5 modes parse; defaults; env-var substitution; `isStorageStateValid()` |
| `SsoAuthManagerTest` | `NONE` is no-op; `STORAGE_STATE` throws on missing file; `CLIENT_CREDENTIALS` with mock endpoint |
| `StorageStateAuthTest` | Parses fixture JSON; extracts cookies; extracts Bearer token; applies to request |
| `ClientCredentialsAuthTest` | Sends correct form body; parses token response; refreshes on expiry; handles errors |
| `BBotAuthExceptionTest` | Constructors; message formatting |

### Documentation Updates

| File | Changes |
|------|---------|
| `README.md` | New "Authentication" section with mode table and quick-start |
| `CLAUDE.md` | Add auth conventions: never commit secrets, use env-var HOCON refs |
| `IMPLEMENTATION_PLAN.md` | M13 status tracking |
| `reference.conf` | Inline docs for every `b-bot.auth.*` key |

### Quality Gate — G13h

```bash
# G13h.1 — ≥ 20 new tests in auth-related test classes
grep -rn "@Test" b-bot-core/src/test/java/com/bbot/core/auth/   # ≥ 20

# G13h.2 — JaCoCo coverage maintained ≥ 65%
mvn verify -pl b-bot-core   # JaCoCo check passes

# G13h.3 — Javadoc zero warnings
mvn javadoc:javadoc -pl b-bot-core

# G13h.4 — Full regression green
mvn verify -pl b-bot-core,b-bot-sandbox
```

---

## New File Summary

| File | Package | Purpose |
|------|---------|---------|
| `SsoAuthConfig.java` | `com.bbot.core.auth` | Config record — parses `b-bot.auth` block |
| `SsoAuthManager.java` | `com.bbot.core.auth` | Orchestrator — interactive login, storageState, OAuth |
| `ClientCredentialsAuth.java` | `com.bbot.core.auth` | OAuth2 client_credentials `AuthStrategy` impl |
| `StorageStateAuth.java` | `com.bbot.core.rest` | `AuthStrategy` that reads Playwright storageState JSON |
| `BBotAuthException.java` | `com.bbot.core.exception` | Auth-specific exception (expired session, OAuth failure) |
| `SsoAuthConfigTest.java` | `com.bbot.core.auth` (test) | Config parsing tests |
| `SsoAuthManagerTest.java` | `com.bbot.core.auth` (test) | Manager logic tests |
| `StorageStateAuthTest.java` | `com.bbot.core.rest` (test) | storageState JSON parsing tests |
| `ClientCredentialsAuthTest.java` | `com.bbot.core.auth` (test) | OAuth flow tests |
| `BBotAuthExceptionTest.java` | `com.bbot.core.exception` (test) | Exception hierarchy tests |

## Modified File Summary

| File | Change |
|------|--------|
| `reference.conf` | Add `b-bot.auth { ... }` block with defaults |
| `BBotConfig.java` | Add `getSsoAuthConfig()` method |
| `PlaywrightManager.java` | `initContext()` loads storageState from disk |
| `AuthStrategy.java` | Add `fromStorageState(Path)` factory method |
| `RestProbe.java` | Auto-detect auth mode when no explicit strategy set |
| `BrowserLifecycle.java` | No change (auth is internal to impl) |
| Template `Hooks.java` | Add `SsoAuthManager.ensureAuthenticated()` call |
| Template `application-uat.conf` | Example SSO config |
| Template `application-ci.conf` | Example CI OAuth config |

---

## Execution Order & Rationale

```
M13a → M13b → M13c → M13d → M13e → M13f → M13g → M13h
```

1. **M13a first** — config schema is the foundation; everything reads from it
2. **M13b next** — core auth manager; can be tested in isolation
3. **M13c** — wire browser context; enables manual end-to-end testing
4. **M13d** — REST auth; completes the "both browser and API are authenticated" story
5. **M13e** — CI mode; independent of browser auth, can be developed in parallel with M13d
6. **M13f** — staleness/refresh; requires M13b+M13c+M13e to be stable first
7. **M13g** — template integration; requires all core pieces
8. **M13h** — tests + docs; wraps everything up

**Invariant:** `mvn verify -pl b-bot-core,b-bot-sandbox` passes after every sub-step
(mode=none is always the default, so existing tests never see auth logic).

---

## Risk Register

| Risk | Mitigation |
|------|-----------|
| Playwright `storageState` doesn't capture all cookies (HttpOnly, SameSite=Strict) | Playwright captures **all** cookies including HttpOnly. Verified in Playwright docs. |
| SSO session expires mid-suite (> 1h run) | M13f detects 401 and throws actionable `BBotAuthException`. For clientCredentials mode, auto-refresh before expiry. |
| Corporate proxy/firewall blocks Playwright browser launch | Use `launchPersistentContext()` with the system Chrome profile, or set `HTTPS_PROXY` env var (Playwright respects it). |
| MS Authenticator push is slow / user misses it | `loginTimeout = 120s` is generous. Interactive mode shows clear console message: "Approve the MFA request on your device." |
| CI service principal doesn't have MFA exemption | This is an **infrastructure prerequisite** — documented in README. Service principals (app registrations) do not go through MFA by design in Azure AD. |
| storageState JSON contains sensitive tokens on disk | File is written to `target/` (gitignored). Docs warn never to commit. CI uses in-memory token (no file). |
| Different SSO providers (Azure AD, Ping, ADFS, Okta) | The approach is **SSO-provider-agnostic** — it captures browser state *after* any SSO flow completes, regardless of the identity provider. |

---

## Real-World Usage Examples

### Developer on UBS Dev Machine (first time)

```bash
# First run — interactive login
mvn verify -pl pt-blotter-regression-template \
    -Db-bot.env=uat \
    -Db-bot.auth.mode=interactive \
    -Db-bot.browser.headless=false

# → Browser opens, SSO login page appears
# → Developer enters credentials, approves MS Authenticator
# → storageState saved to target/auth/ubs-sso-state.json
# → All 24 scenarios run against real UAT — no more login prompts
```

### Developer on UBS Dev Machine (subsequent runs)

```bash
# Subsequent runs — reuse cached session (fast, no browser popup)
mvn verify -pl pt-blotter-regression-template \
    -Db-bot.env=uat \
    -Db-bot.auth.mode=auto

# → Cached session is < 4h old → reused → no login
# → If expired → falls back to interactive automatically
```

### Jenkins CI Pipeline

```bash
# CI — service principal, no human involved
mvn verify -pl pt-blotter-regression-template \
    -Db-bot.env=ci

# application-ci.conf has mode=clientCredentials
# B_BOT_CLIENT_ID and B_BOT_CLIENT_SECRET from Jenkins credentials
# → OAuth token acquired, injected into browser context + REST calls
# → Fully headless, fully automated
```

