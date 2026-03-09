import type { Inquiry } from './types'

// ── Portfolio identifiers ─────────────────────────────────────────────────────
// Two portfolios:  PT1 holds 3 line items, PT2 holds 2.
// Format: PT_BBG_<YYYYMMDD>_<4-char hex hash>
export const PT1 = 'PT_BBG_20260306_3F7A'
export const PT2 = 'PT_BBG_20260306_9C2E'

// ── Reference mid-prices ──────────────────────────────────────────────────────
const MID_PRICES: Record<string, number> = {
  'US912828YJ02': 99.375,   // UST 4.25% 2034    — near par
  'XS2346573523': 101.125,  // EUR IG Corp 3.5%   — slight premium
  'US38141GXZ20': 100.875,  // Goldman Sachs 5.15% — near par
  'GB0031348658': 95.250,   // UK Gilt 1.25%      — below par (low coupon)
  'FR0014004L86': 98.750,   // OAT 0.75%          — below par
}

const HALF_PRICE_SPREAD = 0.125  // pts bid-ask half-spread

/** Formats a combined "bid / ask" price string at 4 decimal places. */
export function makePriceCell(mid: number, halfSpread = HALF_PRICE_SPREAD): string {
  return `${(mid - halfSpread).toFixed(4)} / ${(mid + halfSpread).toFixed(4)}`
}

/** Formats a combined "bid_bp / ask_bp" spread string at 2 decimal places. */
export function makeSpreadCell(bidBp: number, askBp: number): string {
  return `${bidBp.toFixed(2)} / ${askBp.toFixed(2)}`
}

/** Returns a fresh copy of the five design-contract seed inquiries across two portfolios. */
export function buildSeedInquiries(): Inquiry[] {
  const pc = (isin: string) => makePriceCell(MID_PRICES[isin])
  return [
    // ── Portfolio 1 ────────────────────────────────────────────────────────────
    {
      id: 'INQ-001', ptId: PT1, ptLineId: `${PT1}_1`,
      isin: 'US912828YJ02', description: 'UST 4.25% 2034',
      maturity: '2034-11-15', coupon: 4.250, notional: 10_000_000,
      side: 'BUY', client: 'BLACKROCK', status: 'PENDING',
      twPrice:  pc('US912828YJ02'), twSpread:  makeSpreadCell(45.5, 47.0),
      cpPrice:  pc('US912828YJ02'), cpSpread:  makeSpreadCell(45.0, 47.5),
      cbbPrice: pc('US912828YJ02'), cbbSpread: makeSpreadCell(45.2, 46.8),
      pricingAction: null, price: null, spread: null, quotedPrice: null, quotedSpread: null,
    },
    {
      id: 'INQ-002', ptId: PT1, ptLineId: `${PT1}_2`,
      isin: 'XS2346573523', description: 'EUR IG Corp 3.5% 2029',
      maturity: '2029-03-20', coupon: 3.500, notional: 5_000_000,
      side: 'SELL', client: 'PIMCO', status: 'PENDING',
      twPrice:  pc('XS2346573523'), twSpread:  makeSpreadCell(120.0, 122.0),
      cpPrice:  pc('XS2346573523'), cpSpread:  makeSpreadCell(119.5, 122.5),
      cbbPrice: pc('XS2346573523'), cbbSpread: makeSpreadCell(120.2, 121.8),
      pricingAction: null, price: null, spread: null, quotedPrice: null, quotedSpread: null,
    },
    {
      id: 'INQ-003', ptId: PT1, ptLineId: `${PT1}_3`,
      isin: 'US38141GXZ20', description: 'Goldman Sachs 5.15% 2026',
      maturity: '2026-05-22', coupon: 5.150, notional: 8_000_000,
      side: 'BUY', client: 'VANGUARD', status: 'PENDING',
      twPrice:  pc('US38141GXZ20'), twSpread:  makeSpreadCell(95.0, 97.0),
      cpPrice:  pc('US38141GXZ20'), cpSpread:  makeSpreadCell(94.5, 97.5),
      cbbPrice: pc('US38141GXZ20'), cbbSpread: makeSpreadCell(95.2, 96.8),
      pricingAction: null, price: null, spread: null, quotedPrice: null, quotedSpread: null,
    },
    // ── Portfolio 2 ────────────────────────────────────────────────────────────
    {
      id: 'INQ-004', ptId: PT2, ptLineId: `${PT2}_1`,
      isin: 'GB0031348658', description: 'UK Gilt 1.25% 2027',
      maturity: '2027-07-22', coupon: 1.250, notional: 15_000_000,
      side: 'SELL', client: 'FIDELITY', status: 'PENDING',
      twPrice:  pc('GB0031348658'), twSpread:  makeSpreadCell(32.0, 34.0),
      cpPrice:  pc('GB0031348658'), cpSpread:  makeSpreadCell(31.5, 34.5),
      cbbPrice: pc('GB0031348658'), cbbSpread: makeSpreadCell(32.2, 33.8),
      pricingAction: null, price: null, spread: null, quotedPrice: null, quotedSpread: null,
    },
    {
      id: 'INQ-005', ptId: PT2, ptLineId: `${PT2}_2`,
      isin: 'FR0014004L86', description: 'OAT 0.75% 2028',
      maturity: '2028-05-25', coupon: 0.750, notional: 7_500_000,
      side: 'BUY', client: 'AMUNDI', status: 'PENDING',
      twPrice:  pc('FR0014004L86'), twSpread:  makeSpreadCell(55.0, 57.0),
      cpPrice:  pc('FR0014004L86'), cpSpread:  makeSpreadCell(54.5, 57.5),
      cbbPrice: pc('FR0014004L86'), cbbSpread: makeSpreadCell(55.2, 56.8),
      pricingAction: null, price: null, spread: null, quotedPrice: null, quotedSpread: null,
    },
  ]
}
