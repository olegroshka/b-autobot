package com.bbot.core.data;

/**
 * Immutable descriptor for a named REST API action.
 *
 * <p>Actions are declared in {@code b-bot.test-data.api-actions} and represent
 * the API surface of the system under test.  Declaring them in config rather
 * than step definitions means:
 * <ul>
 *   <li>Feature files stay URL-free and self-documenting.</li>
 *   <li>When an endpoint path changes, update the config once — not every scenario.</li>
 *   <li>The config block becomes a living API contract: a reader can see exactly
 *       which HTTP operations the regression suite exercises.</li>
 * </ul>
 *
 * <h2>HOCON declaration</h2>
 * <pre>{@code
 * b-bot.test-data.api-actions {
 *   submit-rfq {
 *     method   = POST
 *     app      = "blotter"
 *     path     = "/api/inquiry"
 *     template = "portfolio-rfq"   # optional; absent means no request body
 *   }
 *   quote-inquiry {
 *     method   = POST
 *     app      = "blotter"
 *     path     = "/api/inquiry/${inquiry_id}/quote"   # ${} resolved from ScenarioState
 *   }
 *   list-inquiries {
 *     method = GET
 *     app    = "blotter"
 *     path   = "/api/inquiries"
 *   }
 * }
 * }</pre>
 *
 * <h2>Step usage</h2>
 * <pre>{@code
 * When I perform "submit-rfq" with bond list "HYPT_1"
 * And  I capture the response field "inquiry_id"
 * When I perform "quote-inquiry"                      // ${inquiry_id} substituted from state
 * Then the response field "status" should be "QUOTED"
 * }</pre>
 *
 * @param name     the action's short name (matches the key in HOCON)
 * @param method   HTTP method: GET, POST, PUT, DELETE (case-insensitive)
 * @param app      app name as declared in {@code b-bot.apps.*}
 * @param path     URL path template; may contain {@code ${key}} tokens
 * @param template optional template name from {@code b-bot.test-data.templates};
 *                 {@code null} means no request body (or empty body)
 */
public record ApiAction(
        String name,
        String method,
        String app,
        String path,
        String template   // nullable
) {}
