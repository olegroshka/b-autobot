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
 * One row in the blotter.  Every nullable field starts null on arrival (PENDING)
 * and is populated as the trader works the inquiry.
 *
 * Reference price cells (twPrice, cpPrice, cbbPrice, twSpread, cpSpread, cbbSpread)
 * hold a combined "bid / ask" string, e.g. "99.0945 / 101.1120".
 * The PriceSimulator ticks these strings at ~400 ms intervals.
 *
 * Skew controls (ref source, ref side, markup, units) live only in the toolbar
 * and are NOT stored per-row — every APPLY uses the current toolbar settings.
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
}
