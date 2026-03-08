package com.bbot.core.exception;

/**
 * Thrown when SSO / OAuth authentication fails.
 *
 * <p>Typical causes:
 * <ul>
 *   <li>Cached storageState file is missing or expired</li>
 *   <li>Interactive login timed out (user did not approve MFA in time)</li>
 *   <li>OAuth client-credentials token request returned a non-200 status</li>
 *   <li>Session expired mid-run (401/403 from the target application)</li>
 * </ul>
 *
 * <p>The message always contains an actionable hint, e.g. which CLI flag
 * to use to re-authenticate.
 *
 * @see com.bbot.core.auth.SsoAuthManager
 */
public class BBotAuthException extends BBotException {

    public BBotAuthException(String message) {
        super(message);
    }

    public BBotAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}

