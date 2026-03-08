import type { GridApi } from 'ag-grid-community'
import { makePriceCell, makeSpreadCell } from './seedData'
import type { Inquiry } from './types'
import { computeApplied } from './priceUtils'

// ── Simulation parameters ────────────────────────────────────────────────────

/** Tick interval in ms — matches TickingCellHelper's 3-second timeout budget. */
const TICK_INTERVAL_MS = 400

/** Gaussian noise standard deviation in price points. */
const PRICE_SIGMA = 0.03

/** Gaussian noise in basis points for spread columns. */
const SPREAD_SIGMA_BP = 0.5

// ── Internal state ────────────────────────────────────────────────────────────

interface BondState {
  midPrice:   number
  twSpreadBid:  number; twSpreadAsk:  number
  cpSpreadBid:  number; cpSpreadAsk:  number
  cbbSpreadBid: number; cbbSpreadAsk: number
}

/** Per-row mutable simulation state, keyed by inquiry id. */
const bondState = new Map<string, BondState>()

// ── Gaussian noise (Box-Muller) ───────────────────────────────────────────────

/** Returns one Gaussian-distributed random variable with mean 0, stddev 1. */
function gaussian(): number {
  let u = 0, v = 0
  while (u === 0) u = Math.random()
  while (v === 0) v = Math.random()
  return Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v)
}

// ── Initialise state from seed rows ──────────────────────────────────────────

/** Parses a "bid / ask" cell string; returns the mid-price, or defaultMid if invalid. */
function parseMidOrDefault(cell: string, defaultMid: number): number {
  if (!cell || !cell.includes(' / ')) return defaultMid
  const [bid, ask] = cell.split(' / ').map(Number)
  return (isNaN(bid) || isNaN(ask)) ? defaultMid : (bid + ask) / 2
}

/** Parses a "bid / ask" cell string; returns [bid, ask], or the given defaults if invalid. */
function parsePairOrDefault(cell: string, defaultBid: number, defaultAsk: number): [number, number] {
  if (!cell || !cell.includes(' / ')) return [defaultBid, defaultAsk]
  const [bid, ask] = cell.split(' / ').map(Number)
  return (isNaN(bid) || isNaN(ask)) ? [defaultBid, defaultAsk] : [bid, ask]
}

/**
 * Populates internal simulation state from the initial row data.
 * Called once when the grid becomes ready.
 */
export function initSimulator(rows: Inquiry[]): void {
  bondState.clear()
  rows.forEach(addRowToSimulator)
}

/**
 * Registers a single row with the simulator so it receives ticking reference
 * prices on every subsequent tick interval.
 *
 * Parses existing "bid / ask" strings from the row when present; falls back to
 * randomised near-par defaults when the row arrives with empty price cells
 * (typical for dynamically-submitted RFQs that carry no market data yet).
 *
 * Safe to call while the simulator is running — the new entry is picked up on
 * the very next tick.  Idempotent: does nothing if the row is already registered.
 */
export function addRowToSimulator(row: Inquiry): void {
  if (bondState.has(row.id)) return

  /* Randomise starting mid so each new bond ticks independently (95–105 range). */
  const defaultMid = 100 + (Math.random() - 0.5) * 10

  const mid                = parseMidOrDefault(row.twPrice,   defaultMid)
  const [twSBid,  twSAsk]  = parsePairOrDefault(row.twSpread,  45 + Math.random() * 5, 47 + Math.random() * 5)
  const [cpSBid,  cpSAsk]  = parsePairOrDefault(row.cpSpread,  45 + Math.random() * 5, 47 + Math.random() * 5)
  const [cbbSBid, cbbSAsk] = parsePairOrDefault(row.cbbSpread, 45 + Math.random() * 5, 47 + Math.random() * 5)

  bondState.set(row.id, {
    midPrice:     mid,
    twSpreadBid:  twSBid,  twSpreadAsk:  twSAsk,
    cpSpreadBid:  cpSBid,  cpSpreadAsk:  cpSAsk,
    cbbSpreadBid: cbbSBid, cbbSpreadAsk: cbbSAsk,
  })
}

// ── Tick function ─────────────────────────────────────────────────────────────

/** Applies one tick to a single bond's simulation state and returns the updates. */
function tickBond(id: string): Partial<Inquiry> {
  const s = bondState.get(id)
  if (!s) return {}

  // Drift mid-price with Gaussian noise
  s.midPrice += gaussian() * PRICE_SIGMA

  // Small independent noise on each spread side
  s.twSpreadBid  += gaussian() * SPREAD_SIGMA_BP
  s.twSpreadAsk  += gaussian() * SPREAD_SIGMA_BP
  s.cpSpreadBid  += gaussian() * SPREAD_SIGMA_BP
  s.cpSpreadAsk  += gaussian() * SPREAD_SIGMA_BP
  s.cbbSpreadBid += gaussian() * SPREAD_SIGMA_BP
  s.cbbSpreadAsk += gaussian() * SPREAD_SIGMA_BP

  // Enforce bid < ask on spreads
  if (s.twSpreadBid  >= s.twSpreadAsk)  s.twSpreadAsk  = s.twSpreadBid  + 0.5
  if (s.cpSpreadBid  >= s.cpSpreadAsk)  s.cpSpreadAsk  = s.cpSpreadBid  + 0.5
  if (s.cbbSpreadBid >= s.cbbSpreadAsk) s.cbbSpreadAsk = s.cbbSpreadBid + 0.5

  return {
    twPrice:   makePriceCell(s.midPrice),
    twSpread:  makeSpreadCell(s.twSpreadBid,  s.twSpreadAsk),
    cpPrice:   makePriceCell(s.midPrice + gaussian() * 0.005),
    cpSpread:  makeSpreadCell(s.cpSpreadBid,  s.cpSpreadAsk),
    cbbPrice:  makePriceCell(s.midPrice + gaussian() * 0.005),
    cbbSpread: makeSpreadCell(s.cbbSpreadBid, s.cbbSpreadAsk),
  }
}

// ── Simulator lifecycle ───────────────────────────────────────────────────────

let intervalId: ReturnType<typeof setInterval> | null = null

/**
 * Starts the price simulation loop.  Each tick applies Gaussian noise to every
 * bond's mid-price and spread, then pushes the delta to AG Grid via
 * {@code applyTransactionAsync} — the same path that triggers
 * {@code ag-cell-data-changed} flash and is detected by {@code TickingCellHelper}.
 *
 * @param api  AG Grid API from the {@code onGridReady} callback.
 */
export function startSimulator(api: GridApi<Inquiry>): void {
  if (intervalId !== null) return   // already running

  intervalId = setInterval(() => {
    const updates: Inquiry[] = []

    api.forEachNode((node) => {
      if (!node.data) return
      const delta = tickBond(node.data.id)
      if (Object.keys(delta).length === 0) return

      // Merge fresh ref prices into the row snapshot
      const updatedRow: Inquiry = { ...node.data, ...delta }

      // Re-derive both price AND spread so they continuously track ref + markup
      if (updatedRow.appliedConfig) {
        const { price, spread } = computeApplied(updatedRow, updatedRow.appliedConfig)
        updatedRow.price  = price
        updatedRow.spread = spread
      }

      updates.push(updatedRow)
    })

    if (updates.length > 0) {
      api.applyTransactionAsync({ update: updates })
    }
  }, TICK_INTERVAL_MS)
}

/** Stops the simulation loop.  Called on component unmount. */
export function stopSimulator(): void {
  if (intervalId !== null) {
    clearInterval(intervalId)
    intervalId = null
  }
}
