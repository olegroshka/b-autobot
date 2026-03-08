package com.bbot.core.exception;

/**
 * Thrown when a REST API call fails or an assertion on the response is violated.
 *
 * <p>Carries structured fields for diagnostic reporting:
 * <ul>
 *   <li>{@link #method()} — HTTP method (GET, POST, etc.)</li>
 *   <li>{@link #url()} — the full URL that was called</li>
 *   <li>{@link #httpStatus()} — the HTTP status code (0 if connection failed)</li>
 *   <li>{@link #responseBody()} — the response body</li>
 * </ul>
 */
public class BBotRestException extends BBotException {

    private final String method;
    private final String url;
    private final int    httpStatus;
    private final String responseBody;

    public BBotRestException(String message, String method, String url,
                              int httpStatus, String responseBody) {
        super(message);
        this.method       = method;
        this.url          = url;
        this.httpStatus   = httpStatus;
        this.responseBody = responseBody;
    }

    public BBotRestException(String message, String method, String url, Throwable cause) {
        super(message, cause);
        this.method       = method;
        this.url          = url;
        this.httpStatus   = 0;
        this.responseBody = "";
    }

    public String method()       { return method; }
    public String url()          { return url; }
    public int    httpStatus()   { return httpStatus; }
    public String responseBody() { return responseBody; }
}

