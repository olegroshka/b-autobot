@blotter-regression
Feature: PT-Blotter Mock UAT Regression — full stack integration demo

  # ─────────────────────────────────────────────────────────────────────────────
  # Philosophy: each section proves one observable aspect of the system.
  #
  #   1. Environment health gate   — the right service versions are deployed.
  #      Fails fast with a clear "start the mock env" message if servers are down.
  #
  #   2. Smoke                     — the UI is reachable and the grid renders.
  #
  #   3. Grid schema & seed data   — the data model is intact on startup.
  #
  #   4. Live price feed           — the ticking simulator is running.
  #
  #   5. Trading workflow          — APPLY → SEND → RELEASE PT core use-case.
  #
  #   6. Access control            — RELEASE PT respects isPTAdmin from Config Service.
  #
  #   7. Config Service            — the permission microservice serves correct data.
  #
  #   8. Deployment Dashboard      — the service registry tracks the right versions.
  #
  # Running the suite:
  #   Start:  scripts/start-mock-uat.sh        (Unix/Mac)
  #           scripts\start-mock-uat.bat        (Windows)
  #   Run:    mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat
  #   Stop:   scripts/stop-mock-uat.sh         (Unix/Mac)
  #           scripts\stop-mock-uat.bat         (Windows)
  #
  # Run a subset by tag:
  #   -Dcucumber.filter.tags="@precondition"   -- health gate only (1 scenario)
  #   -Dcucumber.filter.tags="@smoke"          -- connectivity only (1 scenario)
  #   -Dcucumber.filter.tags="@workflow"       -- trading workflow (4 scenarios)
  #   -Dcucumber.filter.tags="@config-service" -- config REST API (3 scenarios)
  #   -Dcucumber.filter.tags="@deployment"     -- deployment API  (2 scenarios)
  # ─────────────────────────────────────────────────────────────────────────────

  # ── 1. Environment health gate ───────────────────────────────────────────────
  # This scenario runs first. If ANY step fails, the mock UAT environment is not
  # running. Start it before re-running:
  #   Unix/Mac: scripts/start-mock-uat.sh
  #   Windows:  scripts\start-mock-uat.bat
  #
  # This scenario also serves as the formal version-gate for the regression report:
  # proof that the exact service builds under test were deployed at execution time.

  @precondition
  Scenario: Mock UAT stack is live and services are at the tested versions
    Given the "blotter" app is healthy
    And the "config-service" app is healthy
    And the "deployment" app is healthy
    And the service "credit-rfq-blotter" is "RUNNING" at version "v2.4.1"
    And the service "credit-pt-pricer" is "RUNNING" at version "v1.8.3"
    And the service "credit-pt-neg-engine" is "RUNNING" at version "v3.1.0"

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
    And the row with ISIN "US912828YJ02" should have status "PENDING"
    And the row with ISIN "XS2346573523" should have status "PENDING"

  # ── 4. Live price feed ────────────────────────────────────────────────────────
  # The TW price simulator ticks at ~400 ms. This scenario proves it is running.

  @ticking
  Scenario: TW reference price updates within the live feed window
    Given the PT-Blotter is open
    When I wait up to 3 seconds for the "twPrice" cell in row 0 to change value
    Then the "twPrice" cell in row 0 should match the pattern "[0-9]+[.][0-9]+ / [0-9]+[.][0-9]+"

  # ── 5. Trading workflow ───────────────────────────────────────────────────────
  # Core PT-Blotter use case: select a row → set pricing parameters → APPLY → SEND

  @workflow
  Scenario: APPLY with price units sets a numeric price from TW Mid reference
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar source "TW" side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN "US912828YJ02" should be a numeric value

  @workflow
  Scenario: APPLY with spread units sets a numeric spread from CP+ Bid reference
    Given the PT-Blotter is open
    When I select the row with ISIN "XS2346573523"
    And I set the toolbar source "CP+" side "Bid" markup "0" units "bp"
    And I press APPLY
    Then the "spread" for ISIN "XS2346573523" should be a numeric value

  @workflow
  Scenario: SEND captures a sentPrice snapshot and moves the row to QUOTED
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar source "TW" side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN "US912828YJ02" should have status "QUOTED"
    And the "sentPrice" for ISIN "US912828YJ02" should be a numeric value

  @workflow
  Scenario: APPLY only affects selected rows — unselected row price stays blank
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar source "TW" side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN "XS2346573523" should be blank

  # ── 6. Access control — RELEASE PT ───────────────────────────────────────────
  # RELEASE PT is gated by the isPTAdmin flag served by the Config Service.
  # doej is a trader (isPTAdmin=false); smithj is an admin (isPTAdmin=true).

  @access
  Scenario: Trader doej cannot access the RELEASE PT button
    Given the PT-Blotter is open as "doej"
    Then the RELEASE PT button should be disabled

  @access
  Scenario: Admin smithj can release a quoted row — status becomes RELEASED
    Given the PT-Blotter is open as "smithj"
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar source "TW" side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    And I press RELEASE PT
    Then the row with ISIN "US912828YJ02" should have status "RELEASED"

  # ── 7. Config Service ─────────────────────────────────────────────────────────
  # REST-only scenarios — no browser needed.

  @config-service
  Scenario: Config service is healthy and the permission namespace is present
    Given the "config-service" app is healthy
    Then the config namespace "credit.pt.access" should be present

  @config-service
  Scenario: doej does not have PT admin access
    Then the user "doej" should have isPTAdmin "false" in config service

  @config-service
  Scenario: smithj has PT admin access
    Then the user "smithj" should have isPTAdmin "true" in config service

  # ── 8. Deployment Dashboard ───────────────────────────────────────────────────
  # REST-only scenarios — no browser needed.

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
    Then the service "credit-rfq-blotter" is "RUNNING" at version "v2.4.1"
    And the service "credit-pt-pricer" is "RUNNING" at version "v1.8.3"
    And the service "credit-pt-neg-engine" is "RUNNING" at version "v3.1.0"
