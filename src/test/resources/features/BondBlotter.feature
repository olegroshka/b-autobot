@blotter
Feature: PT-Blotter — Fixed Income Bond Portfolio Trading Blotter

  # ──────────────────────────────────────────────────────────────────────────────
  # M0 — Build pipeline spine
  # Goal: Playwright can open the blotter URL served by WireMock.
  # ──────────────────────────────────────────────────────────────────────────────

  @smoke
  Scenario: PT-Blotter page loads
    Given the PT-Blotter is open
    Then the page title should contain "PT-Blotter"

  # ──────────────────────────────────────────────────────────────────────────────
  # M1 — Grid structure visible  [UNHIT — fails until M1 is implemented]
  # Goal: AG Grid renders with all expected columns and seeded rows.
  # ──────────────────────────────────────────────────────────────────────────────

  @m1
  Scenario: Blotter renders expected column groups
    Given the PT-Blotter is open
    Then the grid should display column "isin"
    And the grid should display column "twPrice"
    And the grid should display column "twSpread"
    And the grid should display column "cpPrice"
    And the grid should display column "cbbPrice"
    And the grid should display column "price"
    And the grid should display column "spread"
    And the grid should display column "sentPrice"
    And the grid should display column "sentSpread"
    And the grid should display column "status"

  @m1
  Scenario: Blotter loads with seeded inquiries
    Given the PT-Blotter is open
    Then the grid should have at least 5 rows
    And the row with ISIN "US912828YJ02" should have status "PENDING"
    And the row with ISIN "XS2346573523" should have status "PENDING"
