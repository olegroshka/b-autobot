package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.exception.BBotConfigException;
import com.bbot.core.exception.BBotHealthCheckException;
import com.bbot.core.rest.HttpClientFactory;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable session object that holds the resolved state of all registered
 * {@link AppDescriptor}s and their {@link AppContext}s.
 *
 * <p>Created via the {@link Builder}: register descriptors, call
 * {@link Builder#initialize(BBotConfig)}, then {@link Builder#build()}.
 * Once built, the session is fully immutable (G11.1).
 *
 * <p>Instance methods provide all domain operations (health checks, version
 * assertions, DSL creation). Access via {@link BBotRegistry#session()}.
 *
 * @see BBotRegistry#session()
 */
public final class BBotSession {

    private static final Logger LOG = LoggerFactory.getLogger(BBotSession.class);

    private final Map<String, AppDescriptor<?>> descriptors;
    private final Map<String, AppContext>        contexts;
    private final BBotConfig                     config;

    private BBotSession(Map<String, AppDescriptor<?>> descriptors,
                        Map<String, AppContext> contexts,
                        BBotConfig config) {
        this.descriptors = Collections.unmodifiableMap(new LinkedHashMap<>(descriptors));
        this.contexts    = Collections.unmodifiableMap(new LinkedHashMap<>(contexts));
        this.config      = Objects.requireNonNull(config, "config must not be null");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the active configuration. Never {@code null}. */
    public BBotConfig getConfig() {
        return config;
    }

    /** Returns the set of registered app names. */
    public java.util.Set<String> appNames() {
        return descriptors.keySet();
    }

    // ── DSL factory ───────────────────────────────────────────────────────────

    /**
     * Returns a fresh DSL for the named app, bound to the current scenario's page.
     *
     * @param appName registered name, e.g. {@code "blotter"}
     * @param page    current scenario's Playwright page; {@code null} for REST-only apps
     * @param dslType DSL class for type-safe cast
     */
    @SuppressWarnings("unchecked")
    public <D> D dsl(String appName, Page page, @SuppressWarnings("unused") Class<D> dslType) {
        AppDescriptor<D> desc = (AppDescriptor<D>) requireDescriptor(appName);
        AppContext ctx = requireContext(appName);
        return desc.dslFactory().create(ctx, page);
    }

    // ── Health / version assertions ───────────────────────────────────────────

    /**
     * Asserts the named app's health endpoint returns 2xx.
     * No-op if the descriptor declares no {@link AppDescriptor#healthCheckPath}.
     */
    public void checkHealth(String appName) {
        AppDescriptor<?> desc = requireDescriptor(appName);
        AppContext ctx = requireContext(appName);
        desc.healthCheckPath().ifPresent(path -> {
            String url = ctx.getApiBaseUrl() + path;
            int status = httpGetStatus(url);
            if (status < 200 || status >= 300) {
                throw new BBotHealthCheckException(
                    "Health check FAILED for '" + appName + "': " +
                    "GET " + url + " returned HTTP " + status,
                    appName, url, status, "");
            }
            LOG.debug("Health check OK for '{}': GET {} → HTTP {}", appName, url, status);
        });
    }

    /**
     * Asserts the named app's version endpoint returns JSON containing the expected version.
     * No-op if the descriptor declares no {@link AppDescriptor#versionPath}.
     */
    public void assertVersion(String appName, String expectedVersion) {
        AppDescriptor<?> desc = requireDescriptor(appName);
        AppContext ctx = requireContext(appName);
        desc.versionPath().ifPresent(path -> {
            String body = httpGetBody(ctx.getApiBaseUrl() + path);
            if (!body.contains("\"" + expectedVersion + "\"")) {
                throw new BBotHealthCheckException(
                    "Version mismatch for '" + appName + "': expected \"" +
                    expectedVersion + "\" but response was: " + body,
                    appName, ctx.getApiBaseUrl() + path, 200, body);
            }
            LOG.debug("Version OK for '{}': expected='{}' found in response", appName, expectedVersion);
        });
    }

    /** Checks health of all registered apps that declare a health-check path. */
    public void checkAllHealth() {
        descriptors.keySet().forEach(this::checkHealth);
    }

    /** Returns the {@link AppContext} for the named app, or throws. */
    public AppContext context(String appName) {
        return requireContext(appName);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private AppDescriptor<?> requireDescriptor(String name) {
        AppDescriptor<?> d = descriptors.get(name);
        if (d == null) throw new IllegalArgumentException(
            "No AppDescriptor registered for '" + name + "'. " +
            "Registered names: " + descriptors.keySet());
        return d;
    }

    private AppContext requireContext(String name) {
        AppContext ctx = contexts.get(name);
        if (ctx == null) throw new IllegalStateException(
            "BBotSession has no context for '" + name + "'. " +
            "Registered names: " + contexts.keySet());
        return ctx;
    }

    private HttpClient newHttpClient() {
        Duration timeout = configMs("b-bot.timeouts.healthCheck", 10_000);
        return HttpClientFactory.withTimeout(timeout);
    }

    private int httpGetStatus(String url) {
        try (HttpClient client = newHttpClient()) {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (BBotHealthCheckException e) {
            throw e;
        } catch (Exception e) {
            throw new BBotHealthCheckException(
                "Health check HTTP request failed for: " + url, "", url, e);
        }
    }

    private String httpGetBody(String url) {
        try (HttpClient client = newHttpClient()) {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            return client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        } catch (BBotHealthCheckException e) {
            throw e;
        } catch (Exception e) {
            throw new BBotHealthCheckException(
                "Version check HTTP request failed for: " + url, "", url, e);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private Duration configMs(String key, long defaultMs) {
        if (config.hasPath(key)) return config.getTimeout(key);
        return Duration.ofMillis(defaultMs);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /** Creates a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link BBotSession}.
     *
     * <p>Usage:
     * <pre>{@code
     * BBotSession session = BBotSession.builder()
     *     .register(new BlotterAppDescriptor())
     *     .register(new ConfigServiceDescriptor())
     *     .initialize(BBotConfig.load())
     *     .build();
     * }</pre>
     */
    public static final class Builder {

        private final Map<String, AppDescriptor<?>> descriptors = new LinkedHashMap<>();
        private BBotConfig config;
        private boolean built;

        private Builder() {}

        /** Registers an app descriptor. */
        public Builder register(AppDescriptor<?> descriptor) {
            Objects.requireNonNull(descriptor, "descriptor must not be null");
            if (built) throw new BBotConfigException(
                "BBotSession.Builder: cannot register after build()", descriptor.name());
            descriptors.put(descriptor.name(), descriptor);
            LOG.info("BBotSession.Builder: registered '{}'", descriptor.name());
            return this;
        }

        /**
         * Resolves {@link AppContext}s for every registered descriptor.
         * Must be called exactly once before {@link #build()}.
         */
        public Builder initialize(BBotConfig cfg) {
            Objects.requireNonNull(cfg, "config must not be null");
            if (this.config != null) throw new BBotConfigException(
                "BBotSession.Builder: initialize() already called", "");
            if (built) throw new BBotConfigException(
                "BBotSession.Builder: cannot initialize after build()", "");
            this.config = cfg;
            return this;
        }

        /**
         * Builds an immutable {@link BBotSession}.
         *
         * @throws BBotConfigException if {@link #initialize} was not called
         */
        public BBotSession build() {
            if (config == null) throw new BBotConfigException(
                "BBotSession.Builder: initialize(config) must be called before build()", "");
            if (built) throw new BBotConfigException(
                "BBotSession.Builder: build() already called", "");
            built = true;

            Map<String, AppContext> contexts = new LinkedHashMap<>();
            descriptors.forEach((name, desc) ->
                contexts.put(name, AppContext.fromConfig(name, config)));

            LOG.info("BBotSession built — {} app(s): {}", contexts.size(), contexts.keySet());
            return new BBotSession(descriptors, contexts, config);
        }
    }
}

