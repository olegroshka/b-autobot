import type { GridApi } from 'ag-grid-community'

// ── Column label → colId mapping ─────────────────────────────────────────────
//
// Keys are the human-readable labels used in the filter input syntax, e.g.
//   Portfolio:"PT_BBG_"   →  { ptId: { filterType:'text', type:'contains', filter:'PT_BBG_' } }
//
const LABEL_TO_COL: Record<string, string> = {
  Portfolio:   'ptId',
  ISIN:        'isin',
  Side:        'side',
  Client:      'client',
  Status:      'status',
  Maturity:    'maturity',
  Description: 'description',
}

// ── Columns eligible for double-click auto-filter ────────────────────────────
//
// Must be non-editable and not tick frequently.
// Excludes: all ref-price columns, price, spread, pricingAction, quotedPrice/Spread,
// coupon (numeric), notional (numeric), ptLineId (derived).
//
export const DOUBLE_CLICK_FILTER_COLS = new Set([
  'ptId', 'isin', 'side', 'client', 'status', 'maturity', 'description',
])

// ── formatFilterText ─────────────────────────────────────────────────────────

/** Returns the canonical filter-input text for a given colId + raw value. */
export function formatFilterText(colId: string, value: string): string {
  const entry = Object.entries(LABEL_TO_COL).find(([, id]) => id === colId)
  const label = entry ? entry[0] : colId
  return `${label}:"${value}"`
}

// ── applyGridFilter ───────────────────────────────────────────────────────────

/**
 * Parses `text` and applies the appropriate AG Grid filter.
 *
 *   Label:"value"  →  column-specific text-contains filter via setFilterModel
 *   plain text     →  quick filter across all columns
 *   empty / blank  →  clears all filters
 *
 * While the user is mid-typing a Label: expression (colon present but closing
 * quote not yet typed), no filter is applied — this avoids spurious quick-filter
 * hits during entry.
 */
export function applyGridFilter(api: GridApi, text: string): void {
  const trimmed = text.trim()

  if (!trimmed) {
    api.setFilterModel(null)
    api.updateGridOptions({ quickFilterText: '' })
    return
  }

  // Match complete Label:"value" (double or single quotes)
  const match = trimmed.match(/^(\w+)\s*:\s*"([^"]*)"$/) ??
                trimmed.match(/^(\w+)\s*:\s*'([^']*)'$/)
  if (match) {
    const colId = LABEL_TO_COL[match[1]]
    if (colId) {
      api.setFilterModel({
        [colId]: { filterType: 'text', type: 'contains', filter: match[2] },
      })
      api.updateGridOptions({ quickFilterText: '' })
      return
    }
  }

  // Incomplete Label: expression — don't filter yet
  if (trimmed.includes(':')) return

  // Plain text → quick filter
  api.setFilterModel(null)
  api.updateGridOptions({ quickFilterText: trimmed })
}
