package com.bbot.template.stepdefs;

import com.bbot.core.data.ApiAction;
import com.bbot.core.data.Bond;
import com.bbot.core.data.Portfolio;
import com.bbot.core.data.TestDataConfig;
import com.bbot.core.rest.ScenarioContext;
import com.bbot.core.rest.JsonTemplateEngine;
import com.bbot.core.rest.RestProbe;
import com.bbot.core.rest.RestResponse;
import com.bbot.template.data.BlotterTestData;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic REST API step definitions powered by {@link RestProbe}, {@link JsonTemplateEngine},
 * and the config-driven {@link TestDataConfig}.
 *
 * <p>These steps are app-agnostic — they work against any registered application
 * by referencing its name as declared in {@code b-bot.apps.{name}.apiBase}.
 *
 * <h2>Design principles</h2>
 * <ul>
 *   <li>No hardcoded URLs, ISINs, or dates — everything comes from config or bond lists.</li>
 *   <li>JSON templates live in {@code src/test/resources/templates/} and are referenced
 *       by short name in the feature file.</li>
 *   <li>Values captured from one response (e.g. {@code inquiry_id}) are stored in
 *       {@link com.bbot.core.rest.ScenarioContext} and automatically resolved as
 *       {@code ${inquiry_id}} in subsequent paths and templates.</li>
 *   <li>Bond list references ({@code bond "HYPT_1" field "ISIN1"}) are resolved against
 *       {@code b-bot.test-data.bond-lists.HYPT_1.ISIN1} in your env config.</li>
 * </ul>
 *
 * <h2>Sample scenarios</h2>
 * <pre>{@code
 * When I POST template "credit-rfq" with bond list "HYPT_1" to app "blotter" path "/api/inquiry"
 * Then the response status should be 201
 * And  the response field "status" should be "PENDING"
 * And  the response field "isin" should equal bond "HYPT_1" field "ISIN1"
 * And  I capture the response field "inquiry_id"
 *
 * When I POST template "quote-inquiry" to app "blotter" path "/api/inquiry/${inquiry_id}/quote"
 * Then the response status should be 200
 * And  the response field "status" should be "QUOTED"
 * }</pre>
 */
public class RestApiSteps {

    /**
     * The most recent REST response — shared across steps within the same scenario
     * (Cucumber creates one {@code RestApiSteps} instance per scenario).
     */
    private RestResponse lastResponse;

    private final TestWorld          world;
    private final TestDataConfig     testData;
    private final JsonTemplateEngine templateEngine;
    private final ScenarioContext    scenarioContext;

    public RestApiSteps(TestWorld world) {
        this.world           = world;
        this.testData        = world.session().getConfig().getTestData();
        this.scenarioContext  = world.scenarioContext();
        this.templateEngine  = new JsonTemplateEngine(testData, scenarioContext);
    }

    /** Typed blotter test data — parsed by {@link com.bbot.template.data.BlotterTestDataParser}. */
    private BlotterTestData td() {
        return world.session().context("blotter").getTestData(BlotterTestData.class);
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    /**
     * Renders the named template substituting {@code ${bond.FIELD}} tokens from the
     * named bond list, global test-data scalars, and any previously captured state.
     * Then POSTs the result to {@code {appApiBase}{path}}.
     *
     * <p>Example:
     * <pre>{@code
     * When I POST template "credit-rfq" with bond list "HYPT_1" to app "blotter" path "/api/inquiry"
     * }</pre>
     */
    @When("I POST template {string} with bond list {string} to app {string} path {string}")
    @SuppressWarnings("unused")
    public void postTemplateWithBondList(String template, String bondList,
                                         String app, String path) {
        String body    = templateEngine.render(template, bondList);
        String apiBase = world.session().getConfig().getAppApiBase(app);
        lastResponse   = RestProbe.of(apiBase, scenarioContext).post(path, body);
    }

    /**
     * Renders the named template using global test-data and captured state (no bond list),
     * then POSTs to {@code {appApiBase}{path}}.
     *
     * <p>Use this for templates that don't reference bond-list fields, or for action
     * endpoints (quote, release) whose bodies are empty or contain only workflow state.
     *
     * <p>Path may contain {@code ${key}} tokens resolved from captured state, e.g.:
     * <pre>{@code
     * When I POST template "quote-inquiry" to app "blotter" path "/api/inquiry/${inquiry_id}/quote"
     * }</pre>
     */
    @When("I POST template {string} to app {string} path {string}")
    @SuppressWarnings("unused")
    public void postTemplate(String template, String app, String path) {
        String body    = templateEngine.render(template);
        String apiBase = world.session().getConfig().getAppApiBase(app);
        lastResponse   = RestProbe.of(apiBase, scenarioContext).post(path, body);
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    /**
     * Sends a GET request to {@code {appApiBase}{path}}.
     * {@code ${key}} tokens in the path are resolved from captured state.
     *
     * <p>Example:
     * <pre>{@code
     * When I GET from app "blotter" path "/api/inquiries"
     * When I GET from app "blotter" path "/api/inquiry/${inquiry_id}"
     * }</pre>
     */
    @When("I GET from app {string} path {string}")
    @SuppressWarnings("unused")
    public void getFromApp(String app, String path) {
        String apiBase = world.session().getConfig().getAppApiBase(app);
        lastResponse   = RestProbe.of(apiBase, scenarioContext).get(path);
    }

    // ── Status assertions ─────────────────────────────────────────────────────

    /**
     * Asserts the HTTP status code of the last response.
     */
    @Then("the response status should be {int}")
    public void responseStatusShouldBe(int expected) {
        requireLastResponse("the response status should be");
        lastResponse.assertStatus(expected);
    }

    // ── Field assertions ──────────────────────────────────────────────────────

    /**
     * Asserts that the JSON field at {@code jsonPath} equals {@code expected}.
     * Short-form paths ({@code "status"}) are normalised to {@code "$.status"}.
     *
     * <p>Example:
     * <pre>{@code
     * Then the response field "status" should be "PENDING"
     * Then the response field "$[0].isin" should be "US912828YJ02"
     * }</pre>
     */
    @Then("the response field {string} should be {string}")
    public void responseFieldShouldBe(String jsonPath, String expected) {
        requireLastResponse("the response field");
        lastResponse.assertField(jsonPath, expected);
    }

    /**
     * Asserts that the JSON field at {@code jsonPath} equals the value of
     * {@code fieldName} in the named bond list.
     *
     * <p>Example:
     * <pre>{@code
     * Then the response field "isin" should equal bond "HYPT_1" field "ISIN1"
     * }</pre>
     */
    @Then("the response field {string} should equal bond {string} field {string}")
    public void responseFieldShouldEqualBondField(String jsonPath, String bondList, String field) {
        requireLastResponse("the response field ... should equal bond");
        String expected = testData.resolveBondRef(bondList, field);
        lastResponse.assertField(jsonPath, expected);
    }

    /**
     * Asserts that the JSON field at {@code jsonPath} is non-null and non-blank.
     *
     * <p>Example:
     * <pre>{@code
     * Then the response field "inquiry_id" should not be empty
     * }</pre>
     */
    @Then("the response field {string} should not be empty")
    public void responseFieldShouldNotBeEmpty(String jsonPath) {
        requireLastResponse("the response field ... should not be empty");
        lastResponse.assertFieldNotEmpty(jsonPath);
    }

    // ── Capture ───────────────────────────────────────────────────────────────

    /**
     * Captures the value of {@code jsonPath} from the last response into scenario state.
     * The alias is the last path segment, e.g. {@code "inquiry_id"} or {@code "$.inquiry_id"}
     * both store the value as {@code inquiry_id}.
     *
     * <p>The captured value is then available as {@code ${inquiry_id}} in subsequent
     * path strings and template substitutions.
     *
     * <p>Example:
     * <pre>{@code
     * And I capture the response field "inquiry_id"
     * When I POST template "quote-inquiry" to app "blotter" path "/api/inquiry/${inquiry_id}/quote"
     * }</pre>
     */
    @And("I capture the response field {string}")
    public void captureResponseField(String jsonPath) {
        requireLastResponse("I capture the response field");
        lastResponse.capture(jsonPath);
    }

    /**
     * Captures the value of {@code jsonPath} from the last response into scenario state
     * under an explicit {@code alias}.
     *
     * <p>Example:
     * <pre>{@code
     * And I capture the response field "inquiry_id" as "rfq-id"
     * When I POST template "quote-inquiry" to app "blotter" path "/api/inquiry/${rfq-id}/quote"
     * }</pre>
     */
    @And("I capture the response field {string} as {string}")
    @SuppressWarnings("unused")
    public void captureResponseFieldAs(String jsonPath, String alias) {
        requireLastResponse("I capture the response field ... as");
        lastResponse.capture(jsonPath, alias);
    }

    // ── Named API actions ─────────────────────────────────────────────────────

    /**
     * Executes the named API action declared in the owning app's
     * {@code b-bot.apps.{appName}.api-actions} block.
     * The action supplies method, app, path, and optional template — no hardcoded values
     * in the feature file.
     *
     * <p>Path tokens in curly-brace form ({@code {inquiry_id}}) are resolved at runtime
     * from captured scenario state, e.g.:
     * <pre>{@code
     * When I perform "quote-inquiry"   # path: /api/inquiry/{inquiry_id}/quote
     * }</pre>
     */
    @When("I perform {string}")
    public void performAction(String actionName) {
        ApiAction action = world.session().getConfig().getApiAction(actionName);
        String apiBase = world.session().getConfig().getAppApiBase(action.app());
        String path = resolveActionPath(action.path());
        if ("GET".equalsIgnoreCase(action.method())) {
            lastResponse = RestProbe.of(apiBase, scenarioContext).get(path);
        } else {
            String body = action.template() != null
                    ? templateEngine.render(action.template())
                    : "{}";
            lastResponse = RestProbe.of(apiBase, scenarioContext).post(path, body);
        }
    }

    /**
     * Executes the named API action with a bond list for template token substitution.
     *
     * <p>Example:
     * <pre>{@code
     * When I perform "submit-rfq" with bond list "HYPT_1"
     * }</pre>
     */
    @When("I perform {string} with bond list {string}")
    public void performActionWithBondList(String actionName, String bondList) {
        ApiAction action = world.session().getConfig().getApiAction(actionName);
        String apiBase = world.session().getConfig().getAppApiBase(action.app());
        String path = resolveActionPath(action.path());
        if ("GET".equalsIgnoreCase(action.method())) {
            lastResponse = RestProbe.of(apiBase, scenarioContext).get(path);
        } else {
            String body = action.template() != null
                    ? templateEngine.render(action.template(), bondList)
                    : "{}";
            lastResponse = RestProbe.of(apiBase, scenarioContext).post(path, body);
        }
    }

    /**
     * Submits every bond in the named portfolio as an individual {@code POST /api/inquiry}
     * using the {@code "portfolio-rfq"} template.
     *
     * <p>Each bond's {@code isin}, {@code quantity}, {@code side}, {@code currency} plus
     * the portfolio's {@code pt-id} and {@code settlement-date} are passed as template
     * variables. The returned {@code inquiry_id} is captured as
     * {@code inquiry_id_<lineKey>} (e.g. {@code inquiry_id_line_1}) in scenario state.
     *
     * <p>Asserts status 201 for every bond — the step fails immediately if any submission
     * is rejected.
     *
     * <p>Example:
     * <pre>{@code
     * Given I submit all inquiries for portfolio "HYPT_1"
     * }</pre>
     */
    @Given("I submit all inquiries for portfolio {string}")
    public void submitAllInquiriesForPortfolio(String portfolioName) {
        Portfolio portfolio = td().portfolio(portfolioName);
        String apiBase = world.session().getConfig().getAppApiBase("blotter");
        RestProbe probe = RestProbe.of(apiBase, scenarioContext);

        // Capture the portfolio's PT ID so cancel actions can reference it as ${pt_id}
        scenarioContext.put("pt_id", portfolio.ptId());

        portfolio.bonds().forEach((lineKey, bond) -> {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("isin",            bond.isin());
            vars.put("description",     bond.description());
            vars.put("maturity",        bond.maturity());
            vars.put("coupon",          String.valueOf(bond.coupon()));
            vars.put("quantity",        String.valueOf(bond.quantity()));
            vars.put("notional",        String.valueOf(bond.notional()));
            vars.put("side",            bond.side());
            vars.put("currency",        bond.currency());
            vars.put("client",          bond.client());
            vars.put("pt-id",           portfolio.ptId());
            vars.put("settlement-date", portfolio.settlementDate());

            String body = templateEngine.renderWithContext("portfolio-rfq", vars);
            RestResponse resp = probe.post("/api/inquiry", body);
            resp.assertStatus(201);
            resp.capture("inquiry_id", "inquiry_id_" + lineKey.replace("-", "_"));
        });
    }

    // ── Catalogue-direct bond steps ───────────────────────────────────────────

    /**
     * Renders the action's template using a single bond from the catalogue (no bond list).
     * Bond fields are available in the template as {@code ${bond.isin}},
     * {@code ${bond.description}}, {@code ${bond.maturity}}, {@code ${bond.coupon}}.
     *
     * <p>Example:
     * <pre>{@code
     * When I perform "submit-rfq" with bond "UST-2Y"
     * }</pre>
     */
    @When("I perform {string} with bond {string}")
    public void performActionWithBond(String actionName, String bondId) {
        ApiAction action = world.session().getConfig().getApiAction(actionName);
        String apiBase = world.session().getConfig().getAppApiBase(action.app());
        String path = resolveActionPath(action.path());
        Bond bond = testData.getBond(bondId);
        if ("GET".equalsIgnoreCase(action.method())) {
            lastResponse = RestProbe.of(apiBase, scenarioContext).get(path);
        } else {
            String body = action.template() != null
                    ? templateEngine.renderWithBond(action.template(), bond)
                    : "{}";
            lastResponse = RestProbe.of(apiBase, scenarioContext).post(path, body);
        }
    }

    /**
     * Asserts that the JSON field at {@code jsonPath} equals the ISIN of the
     * bond identified by {@code bondId} in the catalogue.
     *
     * <p>Example:
     * <pre>{@code
     * Then the response field "$[0].isin" should equal the isin of bond "UST-2Y"
     * }</pre>
     */
    @Then("the response field {string} should equal the isin of bond {string}")
    public void responseFieldShouldEqualBondIsin(String jsonPath, String bondId) {
        requireLastResponse("the response field ... should equal the isin of bond");
        lastResponse.assertField(jsonPath, testData.getBond(bondId).isin());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void requireLastResponse(String stepName) {
        if (lastResponse == null)
            throw new AssertionError(
                "No REST response available for step '" + stepName + "'. " +
                "Ensure a GET or POST step runs before this assertion.");
    }

    /**
     * Converts {@code {key}} tokens (used in api-actions conf to avoid HOCON substitution
     * conflicts) to {@code ${key}} so {@link RestProbe} can resolve them from {@link com.bbot.core.rest.ScenarioContext}.
     */
    private static String resolveActionPath(String pathTemplate) {
        return pathTemplate.replaceAll("\\{(\\w[\\w-]*?)}", "\\${$1}");
    }
}
