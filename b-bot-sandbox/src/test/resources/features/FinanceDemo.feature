@finance
Feature: AG Grid Finance Demo — Live Price Feed

  Background:
    Given the Finance Demo page is open

  # ─── Grid structure ──────────────────────────────────────────────────────

  Scenario: Grid renders with expected columns
    Then the grid should display the "ticker" column
    And the grid should display the "instrument" column
    And the grid should display the "totalValue" column
    And the grid should display the "quantity" column

  # ─── Ticking cells ───────────────────────────────────────────────────────

  @ticking
  Scenario: Total Value cell updates within the live feed window
    When I observe cell "totalValue" in row 0
    Then cell "totalValue" in row 0 should change within 3 seconds

  @ticking
  Scenario: Cell flashes to indicate a live data update
    When I wait for the "totalValue" cell in row 0 to flash
    Then the "totalValue" cell should have received at least one tick update

  # ─── Grid interaction ─────────────────────────────────────────────────────

  Scenario: Sorting by ticker column reorders the grid
    When I click the "ticker" column header
    Then the first row ticker should be alphabetically first

  # ─── Column filter ────────────────────────────────────────────────────────

  Scenario: Filtering by Instrument column shows only matching rows
    When I filter the "instrument" column by "Stock"
    Then every visible row in the "instrument" column should contain "Stock"

  Scenario: Clearing the Instrument filter restores all instrument types
    When I filter the "instrument" column by "ETF"
    And I clear the filter on the "instrument" column
    Then the "instrument" column should contain more than one distinct value

