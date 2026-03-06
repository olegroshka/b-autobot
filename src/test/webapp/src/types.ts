// ── Domain types — align with design contract in BLOTTER_DESIGN.md ────────────

export type Status    = 'PENDING' | 'QUOTED' | 'DONE' | 'MISSED'
export type Side      = 'BUY' | 'SELL'

// ── Toolbar state types (not stored on the row) ───────────────────────────────

/** Which external reference-data source to use for APPLY. */
export type RefSource = 'TW' | 'CP+' | 'CBBT'

/** Which side of the combined "bid / ask" cell to anchor on. */
export type RefSide   = 'Bid' | 'Ask' | 'Mid'

/** Whether markup is expressed in price points (c) or basis points (bp). */
export type Units     = 'c' | 'bp'

/**
 * Markup configuration stored on a row after APPLY is pressed.
 * The PriceSimulator re-evaluates `price`/`spread` on every tick using this
 * config so the computed value tracks the live reference price + markup delta.
 * Cleared only when the row status reaches DONE/MISSED (not on SEND — the
 * trader may re-APPLY and re-SEND as many times as needed).
 */
export interface AppliedConfig {
  refSource: RefSource
  refSide:   RefSide
  markup:    number
  units:     Units
}

/**
 * One row in the blotter.  Every nullable field starts null on arrival (PENDING)
 * and is populated as the trader works the inquiry.
 *
 * Reference price cells (twPrice, cpPrice, cbbPrice, twSpread, cpSpread, cbbSpread)
 * hold a combined "bid / ask" string, e.g. "99.0945 / 101.1120".
 * The PriceSimulator ticks these strings at ~400 ms intervals.
 *
 * After APPLY, `appliedConfig` is set and the simulator re-derives `price`/`spread`
 * on every tick.  SEND captures sentPrice/sentSpread but does not clear the config.
 */
export interface Inquiry {
  id:          string
  ptId:        string    // portfolio ID, e.g. PT_BBG_20260306_3F7A
  ptLineId:    string    // line item within portfolio, e.g. PT_BBG_20260306_3F7A_1
  isin:        string
  description: string
  maturity:    string
  coupon:      number
  notional:    number
  side:        Side
  client:      string
  status:      Status

  // Reference prices — ticking, combined "bid / ask" string per source
  twPrice:   string
  twSpread:  string
  cpPrice:   string
  cpSpread:  string
  cbbPrice:  string
  cbbSpread: string

  // Applied values — updated on APPLY; blank until first APPLY
  price:  number | null
  spread: number | null

  // Sent snapshot — captured on SEND; updated on each re-SEND
  sentPrice:  number | null
  sentSpread: number | null

  // Applied markup config — set on APPLY; drives continuous price re-computation
  appliedConfig?: AppliedConfig
}
