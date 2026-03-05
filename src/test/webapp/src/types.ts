// ── Domain types — align with design contract in BLOTTER_DESIGN.md ────────────

export type Status     = 'PENDING' | 'QUOTED' | 'DONE' | 'MISSED'
export type RefSource  = 'TW' | 'CP+' | 'CBBT'
export type Convention = 'Price' | 'Spread'
export type Side       = 'BUY' | 'SELL'

/**
 * One row in the blotter.  Every nullable field starts null on arrival (PENDING)
 * and is populated as the trader works the inquiry.
 *
 * Reference price cells (twPrice, cpPrice, cbbPrice, twSpread, cpSpread, cbbSpread)
 * hold a combined "bid / ask" string, e.g. "99.0945 / 101.1120".
 * The PriceSimulator ticks these strings at ~400 ms intervals.
 */
export interface Inquiry {
  id:          string
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

  // Skew controls — set by toolbar before APPLY
  refSource:  RefSource  | null
  convention: Convention | null
  skewDelta:  number     | null

  // Applied values — updated on APPLY; blank until first APPLY
  price:  number | null
  spread: number | null

  // Sent snapshot — frozen on SEND; updated on each re-SEND
  sentPrice:  number | null
  sentSpread: number | null
}
