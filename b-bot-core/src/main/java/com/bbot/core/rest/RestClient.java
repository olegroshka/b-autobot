package com.bbot.core.rest;

/**
 * Abstraction for HTTP REST operations used by BDD step definitions.
 *
 * <p>The default implementation is {@link RestProbe}, which uses JDK {@link java.net.http.HttpClient}.
 * Consumers may provide custom implementations (e.g. for testing without HTTP)
 * by implementing this interface.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * RestClient client = RestProbe.of(apiBase);
 * RestResponse resp = client.get("/api/inquiry/" + id);
 * resp.assertStatus(200).assertField("status", "QUOTED");
 * }</pre>
 *
 * @see RestProbe
 */
public interface RestClient {

    /** Sends a GET request to {@code path} and returns the response. */
    RestResponse get(String path);

    /** Sends a POST request to {@code path} with {@code jsonBody} and returns the response. */
    RestResponse post(String path, String jsonBody);

    /** Sends a PUT request to {@code path} with {@code jsonBody} and returns the response. */
    RestResponse put(String path, String jsonBody);

    /** Sends a DELETE request to {@code path} and returns the response. */
    RestResponse delete(String path);

    /** Sends a PATCH request to {@code path} with {@code jsonBody} and returns the response. */
    RestResponse patch(String path, String jsonBody);
}

