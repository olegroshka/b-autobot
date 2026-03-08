package com.bbot.core.exception;

/**
 * Thrown when a HOCON config key is missing, a test-data entry cannot be found,
 * or a configuration constraint is violated.
 *
 * <p>Carries the config {@link #key()} that was looked up, so consumers can
 * programmatically identify which configuration is missing.
 */
public class BBotConfigException extends BBotException {

    private final String key;

    public BBotConfigException(String message, String key) {
        super(message);
        this.key = key;
    }

    public BBotConfigException(String message, String key, Throwable cause) {
        super(message, cause);
        this.key = key;
    }

    /** The config key or identifier that could not be resolved. */
    public String key() { return key; }
}

