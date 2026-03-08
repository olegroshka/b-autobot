import type { Inquiry, RefSource, RefSide, Units, AppliedConfig } from './types'

// ── Reference-price parsing ───────────────────────────────────────────────────

/**
 * Splits a combined "bid / ask" cell string into its components.
 * Works for both price (pts) and spread (bp) cells.
 */
export function parseBidAsk(cell: string): { bid: number; ask: number; mid: number } {
  const [bid, ask] = cell.split(' / ').map(Number)
  return { bid, ask, mid: (bid + ask) / 2 }
}

// ── Reference-value extraction ────────────────────────────────────────────────

type RefRow = Pick<Inquiry, 'twPrice' | 'twSpread' | 'cpPrice' | 'cpSpread' | 'cbbPrice' | 'cbbSpread'>

/**
 * Returns the anchor scalar from the row's reference data given toolbar settings.
 * `units='c'` reads the price cell; `units='bp'` reads the spread cell.
 */
export function getRefValue(
  row:       RefRow,
  refSource: RefSource,
  refSide:   RefSide,
  units:     Units,
): number {
  let cell: string
  if (units === 'c') {
    cell = refSource === 'TW' ? row.twPrice : refSource === 'CP+' ? row.cpPrice : row.cbbPrice
  } else {
    cell = refSource === 'TW' ? row.twSpread : refSource === 'CP+' ? row.cpSpread : row.cbbSpread
  }
  const { bid, ask, mid } = parseBidAsk(cell)
  return refSide === 'Bid' ? bid : refSide === 'Ask' ? ask : mid
}

// ── Duration / DV01 estimates ─────────────────────────────────────────────────

/**
 * Approximates modified duration (years) from coupon and maturity date.
 * Uses the Macaulay-duration-at-par formula with annual compounding.
 * Accurate to within ~5% of the true modified duration for IG bonds.
 */
export function estimateModDuration(coupon: number, maturityDate: string): number {
  const now      = Date.now()
  const mat      = new Date(maturityDate).getTime()
  // Math.max(NaN, x) === NaN in JS — guard so rows with unknown maturity get a
  // sensible 5-year default duration rather than NaN poisoning price/spread.
  const yearsToMat = isNaN(mat) ? 5 : (mat - now) / (365.25 * 24 * 3600 * 1000)
  const n        = Math.max(yearsToMat, 0.25)
  if (coupon <= 0) return n                                   // zero-coupon bond
  const r        = coupon / 100
  const macaulay = (1 - Math.pow(1 + r, -n)) / r             // Macaulay @ par
  return macaulay / (1 + r)                                   // modified duration
}

/**
 * DV01 = modified duration × mid-price / 10 000
 * Returns price-points moved per 1 bp change in spread.
 */
export function estimateDV01(modDuration: number, midPrice: number): number {
  return modDuration * Math.max(midPrice, 50) / 10_000
}

// ── Applied-markup computation ────────────────────────────────────────────────

type ComputeRow = RefRow & Pick<Inquiry, 'coupon' | 'maturity'>

/**
 * Computes BOTH the applied price and spread for a row given an AppliedConfig.
 *
 * When `units='c'` (price):
 *   price  = ref_side_price + markup
 *   spread = ref_mid_spread − (price − ref_mid_price) / DV01   [inverse relation]
 *
 * When `units='bp'` (spread):
 *   spread = ref_side_spread + markup
 *   price  = ref_mid_price  − (spread − ref_mid_spread) × DV01
 *
 * The mid values anchor the cross-drive so that zero markup gives mid price and
 * mid spread simultaneously, regardless of which refSide is selected.
 */
export function computeApplied(
  row:    ComputeRow,
  config: AppliedConfig,
): { price: number; spread: number } {
  // Price mid and spread mid for the chosen ref source
  const pCell = config.refSource === 'TW' ? row.twPrice  : config.refSource === 'CP+' ? row.cpPrice  : row.cbbPrice
  const sCell = config.refSource === 'TW' ? row.twSpread : config.refSource === 'CP+' ? row.cpSpread : row.cbbSpread
  const { mid: pMid } = parseBidAsk(pCell)
  const { mid: sMid } = parseBidAsk(sCell)

  const modDur = estimateModDuration(row.coupon, row.maturity)
  const dv01   = Math.max(estimateDV01(modDur, pMid), 1e-6)   // guard /0

  if (config.units === 'c') {
    const price  = getRefValue(row, config.refSource, config.refSide, 'c') + config.markup
    const spread = sMid - (price - pMid) / dv01                // higher price → tighter spread
    return { price, spread }
  } else {
    const spread = getRefValue(row, config.refSource, config.refSide, 'bp') + config.markup
    const price  = pMid - (spread - sMid) * dv01               // wider spread → lower price
    return { price, spread }
  }
}

// ── Pricing action label ──────────────────────────────────────────────────────

/**
 * Returns a short human-readable label describing the applied pricing action.
 * Examples: "TW bid +0.50c"  "CP+ offer -10.0bp"  "CBBT mid"
 *
 * Fixed-income convention: "offer" is used instead of "ask".
 */
export function formatPricingAction(config: AppliedConfig): string {
  const sideLabel = config.refSide === 'Ask' ? 'offer' : config.refSide.toLowerCase()
  if (config.markup === 0) return `${config.refSource} ${sideLabel}`
  const sign = config.markup > 0 ? '+' : ''
  const val  = config.units === 'c'
    ? `${sign}${config.markup.toFixed(2)}c`
    : `${sign}${config.markup.toFixed(1)}bp`
  return `${config.refSource} ${sideLabel} ${val}`
}
