@deployment
Feature: Deployment Dashboard — service registry and health monitor

  # Covers 12 UAT services representing a fixed-income trading platform stack.
  # Three of them (credit-rfq-blotter, credit-pt-pricer, credit-pt-neg-engine)
  # are the systems under test.  The version assertions here feed into the
  # regression report as formal proof of which software versions were tested.
  #
  # Quality gates:
  #   mvn verify -Dcucumber.filter.tags="@deployment and @smoke"    → 2/2
  #   mvn verify -Dcucumber.filter.tags="@deployment and @api"      → 8/8
  #   mvn verify -Dcucumber.filter.tags="@deployment and @versions" → 3/3
  #   mvn verify -Dcucumber.filter.tags="@deployment and @grid"     → 2/2  (needs built assets)
  #   mvn verify -Dcucumber.filter.tags="@deployment and @filter"   → 2/2  (needs built assets)
  #   mvn verify -Dcucumber.filter.tags="@deployment"               → 14/14

  # ── API — registry health ───────────────────────────────────────────────────

  @smoke
  Scenario: Deployment registry is reachable and contains all expected services
    Given the deployment dashboard is available
    Then the dashboard should list at least 12 services

  @smoke @api
  Scenario: All critical credit trading services are registered
    Given the deployment dashboard is available
    Then the deployment API should list service "credit-rfq-blotter"
    And the deployment API should list service "credit-pt-pricer"
    And the deployment API should list service "credit-pt-neg-engine"
    And the deployment API should list service "credit-risk-engine"
    And the deployment API should list service "market-data-gateway"

  # ── API — version evidence (formal proof for regression report) ─────────────
  # Versions are declared in b-bot.test-data.service-versions (application.conf).
  # Update there when deploying a new build; these scenarios stay stable.

  @api @versions
  Scenario: credit-rfq-blotter is deployed and running at the tested version
    Given the deployment dashboard is available
    Then the service "credit-rfq-blotter" should be "RUNNING" at its tested version

  @api @versions
  Scenario: credit-pt-pricer is deployed and running at the tested version
    Given the deployment dashboard is available
    Then the service "credit-pt-pricer" should be "RUNNING" at its tested version

  @api @versions
  Scenario: credit-pt-neg-engine is deployed and running at the tested version
    Given the deployment dashboard is available
    Then the service "credit-pt-neg-engine" should be "RUNNING" at its tested version

  # ── API — status distribution ───────────────────────────────────────────────

  @api @status
  Scenario: Ten services are RUNNING in UAT
    Given the deployment dashboard is available
    Then 10 services should have status "RUNNING"

  @api @status
  Scenario: Exactly one service is STOPPED
    Given the deployment dashboard is available
    Then 1 services should have status "STOPPED"

  @api @status
  Scenario: Exactly one service is FAILED
    Given the deployment dashboard is available
    Then 1 services should have status "FAILED"

  @api @status
  Scenario: regulatory-reporting is STOPPED awaiting maintenance window
    Given the deployment dashboard is available
    Then the service "regulatory-reporting" should have status "STOPPED"

  @api @status
  Scenario: limit-monitor has FAILED and needs attention
    Given the deployment dashboard is available
    Then the service "limit-monitor" should have status "FAILED"

  # ── Browser UI — grid rendering ─────────────────────────────────────────────
  # Requires pre-built deployment UI (committed assets satisfy this for mvn verify).

  @grid
  Scenario: Dashboard grid renders all expected data columns
    Given the deployment dashboard is open
    Then the deployment grid should show column "name"
    And the deployment grid should show column "version"
    And the deployment grid should show column "status"
    And the deployment grid should show column "environment"
    And the deployment grid should show column "team"
    And the deployment grid should show column "lastDeployed"
    And the deployment grid should show column "build"
    And the deployment grid should show column "uptime"

  @grid
  Scenario: Dashboard displays all 12 services in the grid
    Given the deployment dashboard is open
    Then the deployment grid should have at least 12 rows

  # ── Browser UI — filtering ───────────────────────────────────────────────────

  @filter
  Scenario: Filtering by service name prefix narrows the visible rows
    Given the deployment dashboard is open
    When I filter the deployment dashboard by "credit-pt"
    Then the deployment grid should show service "credit-pt-pricer"
    And the deployment grid should show service "credit-pt-neg-engine"

  @filter
  Scenario: Clearing the filter restores all services
    Given the deployment dashboard is open
    When I filter the deployment dashboard by "credit-pt"
    And I clear the deployment filter
    Then the deployment grid should have at least 12 rows
