package model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Root POJO for a Fixed Income trade portfolio REST payload.
 *
 * <p>Matches the standard blotter API contract used by the mock endpoint at
 * {@code https://api.mock-blotter.com/submit}.  All monetary amounts are in the
 * portfolio's base {@link #currency}.
 *
 * <h2>Representative JSON structure</h2>
 * <pre>{@code
 * {
 *   "portfolio_id": "PF-20250303-001",
 *   "trader_id":    "roshkao",
 *   "submitted_at": "2025-03-03T22:00:00Z",
 *   "currency":     "USD",
 *   "status":       "PENDING",
 *   "blotter_id":   "BL-FI-001",
 *   "desk":         "FIXED_INCOME",
 *   "total_face_value":   5000000.00,
 *   "total_market_value": 4937500.00,
 *   "accrued_interest":     12345.67,
 *   "trades": [
 *     { ...Trade fields... }
 *   ]
 * }
 * }</pre>
 *
 * <p>Serialise with Jackson {@code ObjectMapper}; the {@link Builder} constructs
 * request bodies for test steps without needing Lombok.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradePortfolio {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Server-assigned unique identifier returned in the POST response. */
    @JsonProperty("portfolio_id")
    private String portfolioId;

    /** The trader who submitted the portfolio (maps to Gherkin placeholder). */
    @JsonProperty("trader_id")
    private String traderId;

    /** ISO-8601 UTC timestamp of submission. */
    @JsonProperty("submitted_at")
    private String submittedAt;

    // ── Classification ────────────────────────────────────────────────────────

    /** ISO 4217 base currency for all monetary totals. */
    private String currency;

    /**
     * Lifecycle status: PENDING, SUBMITTED, CONFIRMED, REJECTED, CANCELLED.
     */
    private String status;

    /** Blotter system identifier; used to route the portfolio on the server. */
    @JsonProperty("blotter_id")
    private String blotterId;

    /** Trading desk: FIXED_INCOME, RATES, CREDIT, EM, STRUCTURED. */
    private String desk;

    // ── Aggregates ────────────────────────────────────────────────────────────

    /** Sum of face values across all trade legs. */
    @JsonProperty("total_face_value")
    private double totalFaceValue;

    /** Sum of dirty market values across all trade legs. */
    @JsonProperty("total_market_value")
    private double totalMarketValue;

    /** Total accrued interest across all coupon-bearing legs. */
    @JsonProperty("accrued_interest")
    private double accruedInterest;

    // ── Legs ──────────────────────────────────────────────────────────────────

    /** Individual fixed-income trade legs. */
    private List<Trade> trades;

    // ── Constructors ─────────────────────────────────────────────────────────

    public TradePortfolio() {}

    private TradePortfolio(Builder b) {
        this.portfolioId       = b.portfolioId;
        this.traderId          = b.traderId;
        this.submittedAt       = b.submittedAt;
        this.currency          = b.currency;
        this.status            = b.status;
        this.blotterId         = b.blotterId;
        this.desk              = b.desk;
        this.totalFaceValue    = b.totalFaceValue;
        this.totalMarketValue  = b.totalMarketValue;
        this.accruedInterest   = b.accruedInterest;
        this.trades            = b.trades != null
                                    ? Collections.unmodifiableList(b.trades)
                                    : Collections.emptyList();
    }

    public static Builder builder() { return new Builder(); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String      getPortfolioId()      { return portfolioId; }
    public String      getTraderId()         { return traderId; }
    public String      getSubmittedAt()      { return submittedAt; }
    public String      getCurrency()         { return currency; }
    public String      getStatus()           { return status; }
    public String      getBlotterId()        { return blotterId; }
    public String      getDesk()             { return desk; }
    public double      getTotalFaceValue()   { return totalFaceValue; }
    public double      getTotalMarketValue() { return totalMarketValue; }
    public double      getAccruedInterest()  { return accruedInterest; }
    public List<Trade> getTrades()           { return trades; }

    // ── Setters (Jackson deserialization) ────────────────────────────────────

    public void setPortfolioId(String v)       { portfolioId = v; }
    public void setTraderId(String v)          { traderId = v; }
    public void setSubmittedAt(String v)       { submittedAt = v; }
    public void setCurrency(String v)          { currency = v; }
    public void setStatus(String v)            { status = v; }
    public void setBlotterId(String v)         { blotterId = v; }
    public void setDesk(String v)              { desk = v; }
    public void setTotalFaceValue(double v)    { totalFaceValue = v; }
    public void setTotalMarketValue(double v)  { totalMarketValue = v; }
    public void setAccruedInterest(double v)   { accruedInterest = v; }
    public void setTrades(List<Trade> v)       { trades = v; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private String      portfolioId;
        private String      traderId;
        private String      submittedAt;
        private String      currency;
        private String      status;
        private String      blotterId;
        private String      desk;
        private double      totalFaceValue;
        private double      totalMarketValue;
        private double      accruedInterest;
        private List<Trade> trades;

        private Builder() {}

        public Builder portfolioId(String v)      { portfolioId = v;      return this; }
        public Builder traderId(String v)         { traderId = v;         return this; }
        public Builder submittedAt(String v)      { submittedAt = v;      return this; }
        public Builder currency(String v)         { currency = v;         return this; }
        public Builder status(String v)           { status = v;           return this; }
        public Builder blotterId(String v)        { blotterId = v;        return this; }
        public Builder desk(String v)             { desk = v;             return this; }
        public Builder totalFaceValue(double v)   { totalFaceValue = v;   return this; }
        public Builder totalMarketValue(double v) { totalMarketValue = v; return this; }
        public Builder accruedInterest(double v)  { accruedInterest = v;  return this; }
        public Builder trades(List<Trade> v)      { trades = v;           return this; }

        public TradePortfolio build() { return new TradePortfolio(this); }
    }
}
