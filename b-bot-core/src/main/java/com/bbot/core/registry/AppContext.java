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
@SuppressWarnings("unused")
public final class AppContext {

    private final String name;
    private final String webUrl;
    private final String apiBaseUrl;
    private final Map<String, String> users;
    private final Map<String, String> versions;
    private final BBotConfig config;
    private final Object parsedTestData;

    /** Package-private — only {@link BBotSession.Builder} constructs instances. */
    AppContext(String name, String webUrl, String apiBaseUrl,
               Map<String, String> users, Map<String, String> versions,
               BBotConfig config, Object parsedTestData) {
        if (webUrl    != null && !webUrl.endsWith("/"))
            throw new IllegalArgumentException("webUrl must end with '/': " + webUrl);
        if (apiBaseUrl != null && apiBaseUrl.endsWith("/"))
            throw new IllegalArgumentException("apiBase must NOT end with '/': " + apiBaseUrl);

        this.name           = name;
        this.webUrl         = webUrl;
        this.apiBaseUrl     = apiBaseUrl;
        this.users          = Map.copyOf(users);
        this.versions       = Map.copyOf(versions);
        this.config         = config;
        this.parsedTestData = parsedTestData;
    }

    /**
     * Static factory — reads {@code b-bot.apps.{name}.*} from the supplied config.
     * Parsed test data defaults to {@code null}; use
     * {@link #fromConfig(String, BBotConfig, Object)} when parsed data is available.
     * Called by {@link BBotSession.Builder#initialize(BBotConfig)}.
     */
    static AppContext fromConfig(String name, BBotConfig cfg) {
        return fromConfig(name, cfg, null);
    }

    /**
     * Static factory with parsed test data — called by {@link BBotSession.Builder#build()}
     * when the app's {@link AppDescriptor#testDataParser()} returns a non-null parser.
     */
    static AppContext fromConfig(String name, BBotConfig cfg, Object parsedTestData) {
        return new AppContext(
            name,
            cfg.getAppWebUrl(name),
            cfg.getAppApiBase(name),
            cfg.getAppUsers(name),
            cfg.getAppVersions(name),
            cfg,
            parsedTestData
        );
    }

    public String           name()                                   { return name; }
    public String           getWebUrl()                              { return webUrl; }
    public String           getApiBaseUrl()                          { return apiBaseUrl; }
    public Optional<String> getUser(String role)                     { return Optional.ofNullable(users.get(role)); }
    public Optional<String> getExpectedVersion(String serviceName)   { return Optional.ofNullable(versions.get(serviceName)); }
    public Duration         getTimeout(String key)                   { return config.getTimeout(key); }

    /**
     * Returns the {@code path} for a named {@code api-action} declared under this app.
     *
     * <p>Resolves from {@code b-bot.apps.{name}.api-actions.{actionName}.path}.
     *
     * @throws com.bbot.core.exception.BBotConfigException if the action is not declared
     */
    public String getActionPath(String actionName) {
        return config.getAppActionPath(name, actionName);
    }

    /**
     * Returns the domain-specific parsed test data for this app, cast to the given type.
     *
     * <p>The data is populated during {@link BBotSession.Builder#build()} by calling
     * the app descriptor's {@link AppDescriptor#testDataParser()}. Returns {@code null}
     * if the descriptor returned {@code null} from {@code testDataParser()}.
     *
     * @param type the expected type of the parsed test data
     * @param <T>  the type parameter
     */
    @SuppressWarnings("unchecked")
    public <T> T getTestData(Class<T> type) {
        return type.cast(parsedTestData);
    }

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
