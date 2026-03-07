package com.bbot.core.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typesafe Config (HOCON) wrapper with layered environment support and
 * runtime override capability for dynamic-port test servers.
 *
 * <p>All config keys live under the {@code b-bot} namespace
 * (e.g. {@code b-bot.apps.blotter.webUrl}).
 *
 * <h2>Loading order — highest priority wins</h2>
 * <ol>
 *   <li>Runtime overrides — {@link #withOverrides(Map)} at {@code @BeforeAll} time</li>
 *   <li>JVM system properties — {@code -Db-bot.apps.blotter.webUrl=...}</li>
 *   <li>{@code application-{env}.conf} — environment layer (classpath, consuming module)</li>
 *   <li>{@code application.conf} — consumer base defaults (classpath, consuming module)</li>
 *   <li>{@code reference.conf} — core defaults (inside b-bot-core.jar; lowest priority)</li>
 * </ol>
 *
 * <h2>Active environment</h2>
 * Resolved from {@code System.getProperty("b-bot.env")} or env var {@code B_BOT_ENV}.
 * Defaults to {@code "local"}.
 *
 * <h2>Usage in sandbox {@code Hooks.java}</h2>
 * <pre>{@code
 * BBotConfig cfg = BBotConfig.load()
 *     .withOverrides(Map.of(
 *         "b-bot.apps.blotter.webUrl", MockBlotterServer.getBlotterUrl(),
 *         "b-bot.apps.blotter.apiBase", MockBlotterServer.getBaseUrl()
 *     ));
 * }</pre>
 */
public final class BBotConfig {

    private final Config cfg;

    private BBotConfig(Config cfg) {
        this.cfg = cfg;
    }

    /**
     * Loads config using the standard layering strategy.
     * The active environment is read from the {@code b-bot.env} system property
     * or the {@code B_BOT_ENV} environment variable (default: {@code "local"}).
     */
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

    /**
     * Returns a new {@code BBotConfig} with the given key-value pairs layered on top.
     * The original instance is unchanged (immutable).
     *
     * <p>Intended for injecting dynamic values (e.g. mock server ports) that are
     * not known at config-file-write time.
     */
    public BBotConfig withOverrides(Map<String, String> overrides) {
        return new BBotConfig(
            ConfigFactory.parseMap(overrides).withFallback(cfg).resolve()
        );
    }

    // ── Typed accessors ───────────────────────────────────────────────────────

    public String   getString(String key)  { return cfg.getString(key); }
    public boolean  getBoolean(String key) { return cfg.getBoolean(key); }
    public Duration getTimeout(String key) { return cfg.getDuration(key); }
    public boolean  hasPath(String key)    { return cfg.hasPath(key); }

    /**
     * Returns the {@code webUrl} for the named app.
     * Convention: always ends with {@code '/'}.
     * Returns {@code null} when not configured.
     */
    public String getAppWebUrl(String appName) {
        String key = "b-bot.apps." + appName + ".webUrl";
        return cfg.hasPath(key) ? cfg.getString(key) : null;
    }

    /**
     * Returns the {@code apiBase} for the named app.
     * Convention: no trailing slash.
     * Returns {@code null} when not configured.
     */
    public String getAppApiBase(String appName) {
        String key = "b-bot.apps." + appName + ".apiBase";
        return cfg.hasPath(key) ? cfg.getString(key) : null;
    }

    /**
     * Returns the user map for the named app ({@code role -> username}).
     * Returns an empty map if the {@code users} block is absent.
     */
    public Map<String, String> getAppUsers(String appName) {
        String key = "b-bot.apps." + appName + ".users";
        if (!cfg.hasPath(key)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        cfg.getConfig(key).entrySet().forEach(e ->
            result.put(e.getKey(), e.getValue().unwrapped().toString()));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns the versions map for the named app ({@code service-name -> expected-version}).
     * Returns an empty map if the {@code versions} block is absent.
     */
    public Map<String, String> getAppVersions(String appName) {
        String key = "b-bot.apps." + appName + ".versions";
        if (!cfg.hasPath(key)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        cfg.getConfig(key).entrySet().forEach(e ->
            result.put(e.getKey(), e.getValue().unwrapped().toString()));
        return Collections.unmodifiableMap(result);
    }

    /** Raw HOCON access for anything not covered by the typed accessors. */
    public Config raw() { return cfg; }
}
