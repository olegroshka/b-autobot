package com.bbot.core.exception;

import java.time.Duration;

/**
 * Thrown when an AG Grid row cannot be found within the timeout budget.
 *
 * <p>Carries structured fields for diagnostic reporting:
 * <ul>
 *   <li>{@link #colId()} — the column searched</li>
 *   <li>{@link #cellText()} — the value searched for</li>
 *   <li>{@link #timeout()} — the timeout that was exhausted</li>
 * </ul>
 */
public class BBotGridRowNotFoundException extends BBotException {

    private final String   colId;
    private final String   cellText;
    private final Duration timeout;

    public BBotGridRowNotFoundException(String message, String colId, String cellText, Duration timeout) {
        super(message);
        this.colId    = colId;
        this.cellText = cellText;
        this.timeout  = timeout;
    }

    public String   colId()    { return colId; }
    public String   cellText() { return cellText; }
    public Duration timeout()  { return timeout; }
}

