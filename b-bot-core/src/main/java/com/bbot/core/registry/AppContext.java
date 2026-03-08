package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Per-environment, per-app resolved runtime context.
 *
 * <p>Created once per registered app during {@link BBotSession.Builder#initialize(BBotConfig)}.
 * Passed to every {@link DslFactory#create} call, giving the DSL everything it needs:
 * resolved URL(s), users, timeouts — zero static mock-server calls.
 *
 * <h2>HOCON source paths (all under {@code b-bot.apps.{name}})</h2>
 * <ul>
 *   <li>{@code .webUrl}    → {@link #getWebUrl()}      — ends with {@code '/'}, null if REST-only</li>
 *   <li>{@code .apiBase}   → {@link #getApiBaseUrl()}  — no trailing slash, null if UI-only</li>
 *   <li>{@code .users.*}   → {@link #getUser(String)}</li>
 *   <li>{@code .versions.*}→ {@link #getExpectedVersion(String)}</li>
 *   <li>{@code b-bot.timeouts.*} → {@link #getTimeout(String)}</li>
 * </ul>
 *
 * <h2>URL conventions</h2>
 * {@code webUrl} always ends with {@code '/'} (validated on construction).
 * {@code apiBase} never has a trailing slash (validated on construction).
 */
public final class AppContext {

    private final String name;
    private final String webUrl;
    private final String apiBaseUrl;
    private final Map<String, String> users;
    private final Map<String, String> versions;
    private final BBotConfig config;

    /** Package-private — only {@link BBotRegistry} constructs instances. */
    AppContext(String name, String webUrl, String apiBaseUrl,
               Map<String, String> users, Map<String, String> versions,
               BBotConfig config) {
        if (webUrl    != null && !webUrl.endsWith("/"))
            throw new IllegalArgumentException("webUrl must end with '/': " + webUrl);
        if (apiBaseUrl != null && apiBaseUrl.endsWith("/"))
            throw new IllegalArgumentException("apiBase must NOT end with '/': " + apiBaseUrl);

        this.name       = name;
        this.webUrl     = webUrl;
        this.apiBaseUrl = apiBaseUrl;
        this.users      = Map.copyOf(users);
        this.versions   = Map.copyOf(versions);
        this.config     = config;
    }

    /**
     * Static factory — reads {@code b-bot.apps.{name}.*} from the supplied config.
     * Called by {@link BBotSession.Builder#initialize(BBotConfig)}.
     */
    static AppContext fromConfig(String name, BBotConfig cfg) {
        return new AppContext(
            name,
            cfg.getAppWebUrl(name),
            cfg.getAppApiBase(name),
            cfg.getAppUsers(name),
            cfg.getAppVersions(name),
            cfg
        );
    }

    public String           name()                                   { return name; }
    public String           getWebUrl()                              { return webUrl; }
    public String           getApiBaseUrl()                          { return apiBaseUrl; }
    public Optional<String> getUser(String role)                     { return Optional.ofNullable(users.get(role)); }
    public Optional<String> getExpectedVersion(String serviceName)   { return Optional.ofNullable(versions.get(serviceName)); }
    public Duration         getTimeout(String key)                   { return config.getTimeout(key); }

    /**
     * Cross-app URL access — e.g. the blotter DSL needs the config-service {@code apiBase}
     * to build its startup URL ({@code ?configUrl=...}).
     * This is a config read, not a class-level coupling to another DSL.
     */
    public String getOtherAppApiBase(String otherAppName) {
        return config.getAppApiBase(otherAppName);
    }

    /** Full config access for anything not covered by the typed accessors. */
    public BBotConfig config() { return config; }
}
