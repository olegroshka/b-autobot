package com.bbot.sandbox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single fixed-income trade leg within a {@link TradePortfolio}.
 *
 * <p>JSON field names use snake_case to match the standard Fixed Income blotter
 * REST contract. Jackson maps them to camelCase Java fields via {@link JsonProperty}.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "trade_id": "TR-1711234567890",
 *   "isin": "US912828ZL70",
 *   "security_name": "US Treasury 4.25% 2027",
 *   "asset_class": "GOVERNMENT_BOND",
 *   "side": "BUY",
 *   "quantity": 1000000,
 *   "price": 98.75,
 *   "yield": 4.52,
 *   "coupon_rate": 4.25,
 *   "maturity_date": "2027-03-15",
 *   "face_value": 1000000.00,
 *   "market_value": 987500.00,
 *   "currency": "USD",
 *   "settlement_date": "2025-03-05"
 * }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trade {

    @JsonProperty("trade_id")
    private String tradeId;

    /** ISIN (International Securities Identification Number). */
    private String isin;

    /** CUSIP — North American securities identifier (alternative to ISIN). */
    private String cusip;

    @JsonProperty("security_name")
    private String securityName;

    /**
     * Asset class: GOVERNMENT_BOND, CORPORATE_BOND, MUNICIPAL_BOND,
     * MORTGAGE_BACKED, ASSET_BACKED, CONVERTIBLE, etc.
     */
    @JsonProperty("asset_class")
    private String assetClass;

    /** Trade direction: BUY or SELL. */
    private String side;

    /** Face-value quantity (par amount) of the bond. */
    private long quantity;

    /** Clean price as percentage of par (e.g. 98.75 = $987.50 per $1000 face). */
    private double price;

    /** Yield-to-maturity at time of trade. */
    private double yield;

    /** Annual coupon rate expressed as a percentage (e.g. 4.25 = 4.25%). */
    @JsonProperty("coupon_rate")
    private double couponRate;

    /** ISO-8601 maturity date of the security (YYYY-MM-DD). */
    @JsonProperty("maturity_date")
    private String maturityDate;

    /** Total face value of this leg in settlement currency. */
    @JsonProperty("face_value")
    private double faceValue;

    /** Dirty price × quantity (includes accrued interest). */
    @JsonProperty("market_value")
    private double marketValue;

    /** ISO 4217 currency code for this leg (may differ from portfolio base). */
    private String currency;

    /** ISO-8601 settlement date (T+2 convention for most government bonds). */
    @JsonProperty("settlement_date")
    private String settlementDate;

    // ── Constructors ─────────────────────────────────────────────────────────

    public Trade() {}

    private Trade(Builder b) {
        this.tradeId        = b.tradeId;
        this.isin           = b.isin;
        this.cusip          = b.cusip;
        this.securityName   = b.securityName;
        this.assetClass     = b.assetClass;
        this.side           = b.side;
        this.quantity       = b.quantity;
        this.price          = b.price;
        this.yield          = b.yield;
        this.couponRate     = b.couponRate;
        this.maturityDate   = b.maturityDate;
        this.faceValue      = b.faceValue;
        this.marketValue    = b.marketValue;
        this.currency       = b.currency;
        this.settlementDate = b.settlementDate;
    }

    public static Builder builder() { return new Builder(); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getTradeId()        { return tradeId; }
    public String getIsin()           { return isin; }
    public String getCusip()          { return cusip; }
    public String getSecurityName()   { return securityName; }
    public String getAssetClass()     { return assetClass; }
    public String getSide()           { return side; }
    public long   getQuantity()       { return quantity; }
    public double getPrice()          { return price; }
    public double getYield()          { return yield; }
    public double getCouponRate()     { return couponRate; }
    public String getMaturityDate()   { return maturityDate; }
    public double getFaceValue()      { return faceValue; }
    public double getMarketValue()    { return marketValue; }
    public String getCurrency()       { return currency; }
    public String getSettlementDate() { return settlementDate; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private String tradeId;
        private String isin;
        private String cusip;
        private String securityName;
        private String assetClass;
        private String side;
        private long   quantity;
        private double price;
        private double yield;
        private double couponRate;
        private String maturityDate;
        private double faceValue;
        private double marketValue;
        private String currency;
        private String settlementDate;

        private Builder() {}

        public Builder tradeId(String v)        { tradeId = v;        return this; }
        public Builder isin(String v)           { isin = v;           return this; }
        public Builder cusip(String v)          { cusip = v;          return this; }
        public Builder securityName(String v)   { securityName = v;   return this; }
        public Builder assetClass(String v)     { assetClass = v;     return this; }
        public Builder side(String v)           { side = v;           return this; }
        public Builder quantity(long v)         { quantity = v;       return this; }
        public Builder price(double v)          { price = v;          return this; }
        public Builder yield(double v)          { yield = v;          return this; }
        public Builder couponRate(double v)     { couponRate = v;     return this; }
        public Builder maturityDate(String v)   { maturityDate = v;   return this; }
        public Builder faceValue(double v)      { faceValue = v;      return this; }
        public Builder marketValue(double v)    { marketValue = v;    return this; }
        public Builder currency(String v)       { currency = v;       return this; }
        public Builder settlementDate(String v) { settlementDate = v; return this; }

        public Trade build() { return new Trade(this); }
    }
}
