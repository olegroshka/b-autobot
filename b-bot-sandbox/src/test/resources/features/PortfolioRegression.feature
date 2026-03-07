@portfolio @regression
Feature: Portfolio Hybrid Data — REST submission verified in AG Grid blotter

  # ─────────────────────────────────────────────────────────────────────────────
  # SELF-CONTAINED scenarios (@portfolio)
  # Use WireMock for the REST API and a CDN-free mock blotter DOM for the UI.
  # Run with:  mvn test -Dcucumber.filter.tags="@portfolio"
  # ─────────────────────────────────────────────────────────────────────────────

  Scenario: Submitted portfolio ID appears in the blotter grid
    Given the user from role "trader" submits a portfolio via REST API
    And the blotter is populated with the API response
    Then the AG Grid should display the 'Portfolio ID' from the API response in the first row

  Scenario: Submitted portfolio field values match between API response and blotter grid
    Given the user from role "trader" submits a portfolio via REST API
    And the blotter is populated with the API response
    Then verify cell "portfolioId" in row "0" matches the API field "portfolio_id"
    And verify cell "traderId" in row "0" matches the API field "trader_id"
    And verify cell "desk" in row "0" matches the API field "desk"
    And verify cell "currency" in row "0" matches the API field "currency"
    And verify cell "totalMarketValue" in row "0" matches the API field "total_market_value"
    And verify cell "accruedInterest" in row "0" matches the API field "accrued_interest"

  # ── REST contract checks ──────────────────────────────────────────────────

  Scenario: Submission returns HTTP 201 with a non-blank Portfolio ID
    Given the user from role "trader" submits a portfolio via REST API
    Then the API response status should be 201
    And the API response should contain a non-blank 'portfolio_id'

  Scenario: Submission payload is rejected for an unknown trader
    Given the user from role "unknown" submits a portfolio via REST API
    Then the API response status should be 404

  # ─────────────────────────────────────────────────────────────────────────────
  # EXTERNAL scenarios (@external)
  # Navigate to the live AG Grid Finance Demo URL.
  # Require internet access — run separately: mvn test -Dcucumber.filter.tags="@external"
  # ─────────────────────────────────────────────────────────────────────────────

  @external @ticking
  Scenario: Blotter grid loads and renders expected Finance Demo columns
    Given the blotter at endpoint "finance-demo" is open
    Then the AG Grid should display the 'ticker' column
    And the AG Grid should display the 'instrument' column
    And the AG Grid should display the 'totalValue' column
    And the AG Grid should display the 'quantity' column

  @external @ticking
  Scenario: GridHarness can locate a row that has scrolled out of the visible viewport
    Given the blotter at endpoint "finance-demo" is open
    When I search the grid for ticker 'MSFT'
    Then the matching row should be visible and the 'ticker' cell should contain 'MSFT'
