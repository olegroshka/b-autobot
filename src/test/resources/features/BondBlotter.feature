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

  # ──────────────────────────────────────────────────────────────────────────────
  # M2 — Ticking reference prices  [UNHIT — fails until M2 is built]
  # Goal: TW/CP+/CBBT price and spread cells update at ~400 ms; flash on each tick.
  # ──────────────────────────────────────────────────────────────────────────────

  @m2 @ticking
  Scenario: Reference price cells update within the live feed window
    Given the PT-Blotter is open
    When I wait up to 3 seconds for the "twPrice" cell in row 0 to change value
    Then the "twPrice" cell in row 0 should match the pattern "\\d+\\.\\d+ / \\d+\\.\\d+"

  @m2 @ticking
  Scenario: TW Price cell flashes on a live update
    Given the PT-Blotter is open
    When I wait for the "twPrice" cell in row 0 to flash
    Then the "twPrice" cell in row 0 should have received at least one tick update

  @m2 @ticking
  Scenario: All three reference sources are ticking
    Given the PT-Blotter is open
    Then within 3 seconds the "cpPrice" cell in row 0 should change value
    And within 3 seconds the "cbbPrice" cell in row 0 should change value

  # ──────────────────────────────────────────────────────────────────────────────
  # M3 — REST inquiry ingestion  [UNHIT — fails until M3 is built]
  # Goal: POST /api/inquiry adds a new PENDING row; unknown ISINs return 404.
  # ──────────────────────────────────────────────────────────────────────────────

  @m3 @api
  Scenario: Inquiry submitted via API appears in blotter as PENDING
    Given the PT-Blotter is open
    When a new inquiry is submitted for ISIN "US38141GXZ20" notional "3000000" side "BUY" client "SCHRODERS"
    Then the blotter API response status should be 201
    And the response should contain a non-blank "inquiry_id"

  @m3 @api
  Scenario: Unknown ISIN is rejected with 404
    Given the PT-Blotter is open
    When a new inquiry is submitted for ISIN "UNKNOWN-ISIN-XYZ"
    Then the blotter API response status should be 404
