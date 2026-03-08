package com.bbot.core.exception;

/**
 * Base exception for all b-bot-core framework errors.
 *
 * <p>All typed exceptions thrown by the framework extend this class, allowing
 * consumers to catch framework errors distinctly from their own test logic:
 * <pre>{@code
 * try {
 *     blotter.openBlotter();
 * } catch (BBotException e) {
 *     // Framework infrastructure failure — not a test assertion failure
 * }
 * }</pre>
 *
 * <p>Subclasses carry structured fields (not just a message string) so consumers
 * can programmatically inspect failures in custom reporters.
 */
public class BBotException extends RuntimeException {

    public BBotException(String message) {
        super(message);
    }

    public BBotException(String message, Throwable cause) {
        super(message, cause);
    }
}

