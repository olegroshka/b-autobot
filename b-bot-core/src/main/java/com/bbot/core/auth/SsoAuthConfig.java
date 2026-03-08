package com.bbot.core.auth;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.exception.BBotAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Immutable configuration for SSO / OAuth authentication, parsed from
 * the {@code b-bot.auth} HOCON block.
 *
 * <p>Use {@link #from(BBotConfig)} to create an instance from the active config.
 * Use {@link #isNone()} to short-circuit auth logic in mock environments.
 *
 * <h2>Configuration keys</h2>
 * <pre>
 *   b-bot.auth.mode                    = none | interactive | storageState | auto | clientCredentials
 *   b-bot.auth.storageStatePath        = target/auth/storage-state.json
 *   b-bot.auth.sessionTtl              = 4h
 *   b-bot.auth.loginUrl                = {@code https://myapp.company.com/}
 *   b-bot.auth.loginTimeout            = 120s
 *   b-bot.auth.loginSuccessIndicator   = pause | urlContains:pattern | element:selector
 *   b-bot.auth.tokenUrl                = {@code https://login.microsoftonline.com/tenant/oauth2/v2.0/token}
 *   b-bot.auth.clientId                = client-id
 *   b-bot.auth.clientSecret            = client-secret
 *   b-bot.auth.scope                   = api://my-api/.default
 *   b-bot.auth.refreshOn401            = true
 * </pre>
 */
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
        String   scope,
        boolean  refreshOn401
) {

    private static final Logger LOG = LoggerFactory.getLogger(SsoAuthConfig.class);

    /**
     * Authentication modes supported by the framework.
     */
    public enum AuthMode {
        /** No authentication -- default for mock environments. */
        NONE,
        /** Opens a headed browser for manual SSO login + MFA approval. */
        INTERACTIVE,
        /** Loads a previously saved Playwright storageState JSON file. */
        STORAGE_STATE,
        /** Tries storageState first; falls back to interactive if expired. */
        AUTO,
        /** OAuth2 client_credentials grant for CI pipelines. */
        CLIENT_CREDENTIALS;

        /**
         * Parses a mode string from HOCON config.
         * Accepted values: {@code none}, {@code interactive}, {@code storageState},
         * {@code auto}, {@code clientCredentials} (case-insensitive).
         *
         * @throws BBotAuthException if the value is unrecognised
         */
        public static AuthMode parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT).trim()) {
                case "none"              -> NONE;
                case "interactive"       -> INTERACTIVE;
                case "storagestate"      -> STORAGE_STATE;
                case "auto"              -> AUTO;
                case "clientcredentials" -> CLIENT_CREDENTIALS;
                default -> throw new BBotAuthException(
                        "Unrecognised b-bot.auth.mode: '" + value + "'. " +
                        "Valid values: none, interactive, storageState, auto, clientCredentials");
            };
        }
    }

    // -- Factory --------------------------------------------------------------

    /**
     * Creates an {@code SsoAuthConfig} from the active {@link BBotConfig}.
     * All keys fall back to defaults declared in {@code reference.conf}.
     */
    public static SsoAuthConfig from(BBotConfig cfg) {
        String prefix = "b-bot.auth.";
        AuthMode mode = AuthMode.parse(cfg.getString(prefix + "mode"));

        SsoAuthConfig result = new SsoAuthConfig(
                mode,
                Path.of(cfg.getString(prefix + "storageStatePath")),
                cfg.getTimeout(prefix + "sessionTtl"),
                cfg.getString(prefix + "loginUrl"),
                cfg.getTimeout(prefix + "loginTimeout"),
                cfg.getString(prefix + "loginSuccessIndicator"),
                cfg.getString(prefix + "tokenUrl"),
                cfg.getString(prefix + "clientId"),
                cfg.getString(prefix + "clientSecret"),
                cfg.getString(prefix + "scope"),
                cfg.getBoolean(prefix + "refreshOn401")
        );

        LOG.debug("SsoAuthConfig loaded -- mode={}, storagePath={}", mode, result.storageStatePath());
        return result;
    }

    // -- Convenience queries --------------------------------------------------

    /** Returns {@code true} if auth is disabled (mode=none). */
    public boolean isNone() {
        return mode == AuthMode.NONE;
    }

    /** Returns {@code true} if this mode uses a storageState file on disk. */
    public boolean usesStorageState() {
        return mode == AuthMode.INTERACTIVE
            || mode == AuthMode.STORAGE_STATE
            || mode == AuthMode.AUTO;
    }

    /**
     * Returns {@code true} if a cached storageState file exists on disk
     * AND is younger than {@link #sessionTtl()}.
     *
     * <p>Returns {@code false} if the file does not exist, is unreadable,
     * or is older than the configured TTL.
     */
    public boolean isStorageStateValid() {
        if (!Files.exists(storageStatePath)) {
            LOG.debug("Storage state file does not exist: {}", storageStatePath);
            return false;
        }
        try {
            Instant lastModified = Files.getLastModifiedTime(storageStatePath).toInstant();
            Duration age = Duration.between(lastModified, Instant.now());
            boolean valid = age.compareTo(sessionTtl) < 0;
            LOG.debug("Storage state age={}, ttl={}, valid={}", age, sessionTtl, valid);
            return valid;
        } catch (IOException e) {
            LOG.warn("Could not read storage state file metadata: {}", e.getMessage());
            return false;
        }
    }

    // -- Validation helpers ---------------------------------------------------

    /**
     * Validates that the required config fields are present for the current mode.
     *
     * @throws BBotAuthException if a required field is blank or missing
     */
    public void validate() {
        switch (mode) {
            case INTERACTIVE, AUTO ->
                requireNonBlank(loginUrl,
                        "b-bot.auth.loginUrl is required for " + mode + " mode. " +
                        "Set it to the URL that triggers the SSO redirect.");
            case STORAGE_STATE -> {
                if (!Files.exists(storageStatePath)) {
                    throw new BBotAuthException(
                            "b-bot.auth.mode=storageState but file does not exist: " + storageStatePath +
                            "\nRun once with -Db-bot.auth.mode=interactive to create it.");
                }
            }
            case CLIENT_CREDENTIALS -> {
                requireNonBlank(tokenUrl,
                        "b-bot.auth.tokenUrl is required for clientCredentials mode.");
                requireNonBlank(clientId,
                        "b-bot.auth.clientId is required for clientCredentials mode. " +
                        "Use ${?B_BOT_CLIENT_ID} in your config.");
                requireNonBlank(clientSecret,
                        "b-bot.auth.clientSecret is required for clientCredentials mode. " +
                        "Use ${?B_BOT_CLIENT_SECRET} in your config or set it as an env var.");
            }
            case NONE -> { /* nothing to validate */ }
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BBotAuthException(message);
        }
    }
}

