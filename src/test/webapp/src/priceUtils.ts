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

/**
 * Returns the anchor scalar from the row's reference data given toolbar settings.
 * `units='c'` reads the price cell; `units='bp'` reads the spread cell.
 */
export function getRefValue(
  row:       Pick<Inquiry, 'twPrice' | 'twSpread' | 'cpPrice' | 'cpSpread' | 'cbbPrice' | 'cbbSpread'>,
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

// ── Applied-markup computation ────────────────────────────────────────────────

/**
 * Computes the applied price or spread for a row given an AppliedConfig.
 * Used both in App.tsx (on APPLY) and in PriceSimulator.ts (on every tick).
 * Returns only the field that changes — either `price` or `spread`.
 */
export function computeApplied(
  row:    Pick<Inquiry, 'twPrice' | 'twSpread' | 'cpPrice' | 'cpSpread' | 'cbbPrice' | 'cbbSpread'>,
  config: AppliedConfig,
): { price?: number; spread?: number } {
  const refValue = getRefValue(row, config.refSource, config.refSide, config.units)
  const computed = refValue + config.markup
  return config.units === 'c' ? { price: computed } : { spread: computed }
}
