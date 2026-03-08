package com.bbot.core.exception;

/**
 * Thrown when a health check or version assertion fails for a registered application.
 *
 * <p>Carries structured fields so consumers can programmatically inspect the failure:
 * <ul>
 *   <li>{@link #appName()} — the registered app name (e.g. "blotter")</li>
 *   <li>{@link #url()} — the URL that was probed</li>
 *   <li>{@link #httpStatus()} — the HTTP status code received (0 if connection failed)</li>
 *   <li>{@link #responseBody()} — the response body (empty if connection failed)</li>
 * </ul>
 */
public class BBotHealthCheckException extends BBotException {

    private final String appName;
    private final String url;
    private final int    httpStatus;
    private final String responseBody;

    public BBotHealthCheckException(String message, String appName, String url,
                                     int httpStatus, String responseBody) {
        super(message);
        this.appName      = appName;
        this.url          = url;
        this.httpStatus   = httpStatus;
        this.responseBody = responseBody;
    }

    public BBotHealthCheckException(String message, String appName, String url, Throwable cause) {
        super(message, cause);
        this.appName      = appName;
        this.url          = url;
        this.httpStatus   = 0;
        this.responseBody = "";
    }

    public String appName()      { return appName; }
    public String url()          { return url; }
    public int    httpStatus()   { return httpStatus; }
    public String responseBody() { return responseBody; }
}

