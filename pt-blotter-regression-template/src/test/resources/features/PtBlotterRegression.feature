@blotter-regression
Feature: PT-Blotter Mock UAT Regression — full stack integration demo

  # ─────────────────────────────────────────────────────────────────────────────
  # Philosophy: each section proves one observable aspect of the system.
  #
  # All ISINs, versions, and user identities are declared in application-mockuat.conf
  # under b-bot.test-data.  Feature files contain no hardcoded data values.
  #
  # Running the suite:
  #   Start:  scripts/start-mock-uat.sh        (Unix/Mac)
  #           scripts\start-mock-uat.bat        (Windows)
  #   Run:    mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat
  #   Stop:   scripts/stop-mock-uat.sh         (Unix/Mac)
  #           scripts\stop-mock-uat.bat         (Windows)
  #
  # Run a subset by tag:
  #   -Dcucumber.filter.tags="@precondition"              -- health gate only    (1 scenario)
  #   -Dcucumber.filter.tags="@smoke"                     -- connectivity only   (1 scenario)
  #   -Dcucumber.filter.tags="@workflow"                  -- trading workflow    (7 scenarios)
  #   -Dcucumber.filter.tags="@access"                    -- access control      (2 scenarios)
  #   -Dcucumber.filter.tags="@config-service"            -- config REST API     (3 scenarios)
  #   -Dcucumber.filter.tags="@deployment"                -- deployment API      (2 scenarios)
  #   -Dcucumber.filter.tags="@rest-probe"                -- REST API probes     (9 scenarios)
  #   -Dcucumber.filter.tags="@portfolio"                 -- portfolio flows     (6 scenarios)
  #   -Dcucumber.filter.tags="@showcase"                  -- full lifecycle demo  (1 scenario)
  #   -Dcucumber.filter.tags="@rest-probe and not @workflow" -- REST-only, no browser (6 scenarios)
  # ─────────────────────────────────────────────────────────────────────────────

  # ── 0. Full lifecycle showcase ───────────────────────────────────────────────
  # A single scenario that walks the complete credit portfolio workflow end-to-end,
  # touching every layer of the framework in one readable narrative:
  #
  #   REST layer   — portfolio bonds POSTed via a conf-declared named action
  #   Grid layer   — submitted bonds appear as live PENDING rows in the AG Grid blotter
  #   Pricing      — TradeWeb Mid reference price applied to both bonds in one pass
  #   Quote        — SEND snapshots the live price; rows transition to QUOTED
  #
  # All ISINs, user roles, API paths, and request bodies are declared in
  # application-mockuat.conf — this scenario contains no hardcoded values.

  @showcase @rest-probe @workflow @portfolio
  Scenario: Full credit portfolio lifecycle — REST submission through to QUOTED
    # 1. REST: POST both portfolio bonds through the blotter inquiry API
    Given I submit all inquiries for portfolio "HYPT_1"

    # 2. Grid: open the blotter — both bonds are visible as PENDING inquiries
    And the PT-Blotter is open
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "PENDING"
    And the row with ISIN from "HYPT_1" field "ISIN2" should have status "PENDING"

    # 3. Pricing: select both bonds, set TW Mid + 0 markup, press APPLY
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I select the row with ISIN from "HYPT_1" field "ISIN2"
    And I set the toolbar source "TW" side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value
    And the "price" for ISIN from "HYPT_1" field "ISIN2" should be a numeric value

    # 4. Quote: SEND — live prices are snapshotted; both inquiries move to QUOTED
    When I press SEND
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "QUOTED"
    And the row with ISIN from "HYPT_1" field "ISIN2" should have status "QUOTED"
    And the "sentPrice" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value
    And the "sentPrice" for ISIN from "HYPT_1" field "ISIN2" should be a numeric value

  # ── 1. Environment health gate ───────────────────────────────────────────────
  # This scenario runs first. If ANY step fails, the mock UAT environment is not
  # running. Start it before re-running:
  #   Unix/Mac: scripts/start-mock-uat.sh
  #   Windows:  scripts\start-mock-uat.bat
  #
  # Versions are declared in b-bot.test-data.service-versions (application-mockuat.conf).

  @precondition
  Scenario: Mock UAT stack is live and services are at the tested versions
    Given the "blotter" app is healthy
    And the "config-service" app is healthy
    And the "deployment" app is healthy
    And the service "credit-rfq-blotter" is "RUNNING" at its tested version
    And the service "credit-pt-pricer" is "RUNNING" at its tested version
    And the service "credit-pt-neg-engine" is "RUNNING" at its tested version

  # ── 2. Smoke ─────────────────────────────────────────────────────────────────

  @smoke
  Scenario: PT-Blotter loads and the AG Grid renders
    Given the PT-Blotter is open
    Then the page title should contain "PT-Blotter"
    And the blotter grid should be visible

  # ── 3. Grid schema and seed data ─────────────────────────────────────────────

  @grid
  Scenario: Blotter renders all expected columns
    Given the PT-Blotter is open
    Then the grid should display column "isin"
    And the grid should display column "twPrice"
    And the grid should display column "price"
    And the grid should display column "spread"
    And the grid should display column "sentPrice"
    And the grid should display column "status"

  @grid
  Scenario: Blotter loads with seeded inquiries in PENDING status
    Given the PT-Blotter is open
    Then the grid should have at least 5 rows
    And the row with ISIN from "HYPT_1" field "ISIN1" should have status "PENDING"
    And the row with ISIN from "HYPT_1" field "ISIN2" should have status "PENDING"

  # ── 4. Live price feed ────────────────────────────────────────────────────────

  @ticking
  Scenario: TW reference price updates within the live feed window
    Given the PT-Blotter is open
    When I wait up to 3 seconds for the "twPrice" cell in row 0 to change value
    Then the "twPrice" cell in row 0 should match the pattern "[0-9]+[.][0-9]+ / [0-9]+[.][0-9]+"

  # ── 5. Trading workflow ───────────────────────────────────────────────────────

  @workflow
  Scenario: APPLY with price units sets a numeric price from TW Mid reference
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar source "TW" side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value

  @workflow
  Scenario: APPLY with spread units sets a numeric spread from CP+ Bid reference
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN2"
    And I set the toolbar source "CP+" side "Bid" markup "0" units "bp"
    And I press APPLY
    Then the "spread" for ISIN from "HYPT_1" field "ISIN2" should be a numeric value

  @workflow
  Scenario: SEND captures a sentPrice snapshot and moves the row to QUOTED
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar source "TW" side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "QUOTED"
    And the "sentPrice" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value

  @workflow
  Scenario: APPLY only affects selected rows — unselected row price stays blank
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar source "TW" side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN from "HYPT_1" field "ISIN2" should be blank

  # ── 6. Access control — RELEASE PT ───────────────────────────────────────────
  # User roles are declared in b-bot.test-data.users (application-mockuat.conf).

  @access
  Scenario: Trader cannot access the RELEASE PT button
    Given the PT-Blotter is open as the trader
    Then the RELEASE PT button should be disabled

  @access
  Scenario: Admin can release a quoted row — status becomes RELEASED
    Given the PT-Blotter is open as the admin
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar source "TW" side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    And I press RELEASE PT
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "RELEASED"

  # ── 7. Config Service ─────────────────────────────────────────────────────────

  @config-service
  Scenario: Config service is healthy and the permission namespace is present
    Given the "config-service" app is healthy
    Then the config namespace "credit.pt.access" should be present

  @config-service
  Scenario: Trader does not have PT admin access
    Then the user from role "trader" should have isPTAdmin "false" in config service

  @config-service
  Scenario: Admin has PT admin access
    Then the user from role "admin" should have isPTAdmin "true" in config service

  # ── 8. Deployment Dashboard ───────────────────────────────────────────────────

  @deployment
  Scenario: All critical credit trading services are registered in the deployment registry
    Given the deployment dashboard is available
    Then the deployment registry should list service "credit-rfq-blotter"
    And the deployment registry should list service "credit-pt-pricer"
    And the deployment registry should list service "credit-pt-neg-engine"
    And the deployment registry should list service "credit-risk-engine"
    And the deployment registry should list service "market-data-gateway"

  @deployment @versions
  Scenario: All tested services are RUNNING at the exact versions under test
    Given the deployment dashboard is available
    Then the service "credit-rfq-blotter" is "RUNNING" at its tested version
    And the service "credit-pt-pricer" is "RUNNING" at its tested version
    And the service "credit-pt-neg-engine" is "RUNNING" at its tested version

  # ── 9. REST API contract — named actions from conf ───────────────────────────
  # All action names, paths, methods, and templates are declared in
  # b-bot.test-data.api-actions (application-mockuat.conf).
  # All ISINs come from bond-lists.  No paths or ISINs hardcoded in this section.

  @rest-probe @api
  Scenario: API contract -- submit RFQ for HYPT_1 bond and verify inquiry accepted
    When I perform "submit-rfq" with bond list "HYPT_1"
    Then the response status should be 201
    And the response field "status" should be "PENDING"
    And the response field "inquiry_id" should not be empty

  @rest-probe @api
  Scenario: API contract -- inquiry list seed matches HYPT_1 bond list in conf
    When I perform "list-inquiries"
    Then the response status should be 200
    And the response field "$[0].status" should be "PENDING"
    And the response field "$[0].isin" should equal bond "HYPT_1" field "ISIN1"

  @rest-probe @api
  Scenario: API contract -- submit RFQ, capture inquiry ID, quote via named actions
    When I perform "submit-rfq" with bond list "HYPT_1"
    Then the response status should be 201
    And the response field "status" should be "PENDING"
    And I capture the response field "inquiry_id"
    When I perform "quote-inquiry"
    Then the response status should be 200
    And the response field "status" should be "QUOTED"

  # ── 10. Dynamic portfolio submission ─────────────────────────────────────────
  # Portfolio structures (ISINs, quantities, PT IDs) declared in
  # b-bot.test-data.portfolios (application-mockuat.conf).

  @rest-probe @api @portfolio
  Scenario: Portfolio submission -- all HYPT_1 bonds accepted and seed data intact in inquiry list
    Given I submit all inquiries for portfolio "HYPT_1"
    When I perform "list-inquiries"
    Then the response status should be 200
    And the response field "$[0].status" should be "PENDING"
    And the response field "$[0].isin" should equal bond "HYPT_1" field "ISIN1"

  @rest-probe @workflow @portfolio
  Scenario: Dynamic portfolio -- HYPT_1 bonds submitted via API, priced and quoted in blotter
    Given I submit all inquiries for portfolio "HYPT_1"
    And the PT-Blotter is open
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "PENDING"
    And the row with ISIN from "HYPT_1" field "ISIN2" should have status "PENDING"
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar source "TW" side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "QUOTED"

  # ── 11. PT cancel actions ─────────────────────────────────────────────────────
  # dealer-cancel and customer-cancel operate at PT level: all line items of the
  # PT move to DEALER_REJECT or CUSTOMER_REJECT in a single call.
  # {pt_id} is captured automatically by "I submit all inquiries for portfolio".

  @rest-probe @api @portfolio
  Scenario: Dealer cancel -- all CANCEL_DEALER_1 PT line items move to DEALER_REJECT
    Given I submit all inquiries for portfolio "CANCEL_DEALER_1"
    When I perform "dealer-cancel"
    Then the response status should be 200
    And the response field "status" should be "DEALER_REJECT"
    And the response field "affected_count" should be "2"

  @rest-probe @api @portfolio
  Scenario: Customer cancel -- all CANCEL_CUSTOMER_1 PT line items move to CUSTOMER_REJECT
    Given I submit all inquiries for portfolio "CANCEL_CUSTOMER_1"
    When I perform "customer-cancel"
    Then the response status should be 200
    And the response field "status" should be "CUSTOMER_REJECT"
    And the response field "affected_count" should be "2"

  @rest-probe @workflow @portfolio
  Scenario: Dealer cancel is visible in blotter -- cancelled PT lines show DEALER_REJECT
    Given I submit all inquiries for portfolio "CANCEL_BLOTTER_1"
    When I perform "dealer-cancel"
    And the PT-Blotter is open
    Then the row with ISIN from "CANCEL_BLOTTER_1" field "ISIN1" should have status "DEALER_REJECT"
    And the row with ISIN from "CANCEL_BLOTTER_1" field "ISIN2" should have status "DEALER_REJECT"
