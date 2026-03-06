import { useRef, useState, useCallback, useEffect } from 'react'
import type { GridApi } from 'ag-grid-community'
import type { Inquiry, RefSource, RefSide, Units } from './types'
import BlotterGrid from './BlotterGrid'
import Toolbar from './Toolbar'
import { sendQuote } from './api'
import './App.css'

// ── APPLY helpers ─────────────────────────────────────────────────────────────

/**
 * Extracts bid, ask, and mid from a combined "bid / ask" cell string.
 * Works for both price cells (pts) and spread cells (bp).
 */
function parseBidAsk(cell: string): { bid: number; ask: number; mid: number } {
  const [bid, ask] = cell.split(' / ').map(Number)
  return { bid, ask, mid: (bid + ask) / 2 }
}

/**
 * Returns the anchor price/spread value from a row given the current toolbar
 * settings.  `units='c'` reads the price cell; `units='bp'` reads the spread cell.
 */
function getRefValue(
  row:       Inquiry,
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

// ── Root component ────────────────────────────────────────────────────────────

/**
 * PT-Blotter root component.
 *
 * Owns the AG Grid API reference and drives the toolbar → APPLY / SEND workflow:
 *   - APPLY: reads toolbar settings, computes price (c) or spread (bp) for each
 *             selected row and pushes via synchronous applyTransaction.
 *   - SEND:  POSTs a quote for each selected row, then stamps status=QUOTED and
 *             captures the sentPrice/sentSpread snapshot.  Non-locking — the row
 *             stays fully editable for re-quote (re-APPLY → re-SEND) cycles.
 */
export default function App() {
  const gridApiRef    = useRef<GridApi<Inquiry> | null>(null)
  const [selectedCount, setSelectedCount] = useState(0)
  const [clock, setClock] = useState('')

  useEffect(() => {
    const tick = () => setClock(new Date().toLocaleTimeString('en-GB', { hour12: false }))
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [])

  const handleGridReady = useCallback((api: GridApi<Inquiry>) => {
    gridApiRef.current = api
  }, [])

  const handleSelectionChanged = useCallback((count: number) => {
    setSelectedCount(count)
  }, [])

  // ── APPLY ─────────────────────────────────────────────────────────────────

  const handleApply = useCallback(
    (refSource: RefSource, refSide: RefSide, markup: number, units: Units) => {
      const api = gridApiRef.current
      if (!api) return

      const updates: Inquiry[] = api.getSelectedRows().map((row) => {
        const refValue = getRefValue(row, refSource, refSide, units)
        const computed = refValue + markup
        return {
          ...row,
          price:  units === 'c'  ? computed : row.price,
          spread: units === 'bp' ? computed : row.spread,
        }
      })

      if (updates.length > 0) api.applyTransaction({ update: updates })
    },
    [],
  )

  // ── SEND ──────────────────────────────────────────────────────────────────

  const handleSend = useCallback(async () => {
    const api = gridApiRef.current
    if (!api) return

    // Snapshot selected rows before any async mutations
    const selected = api.getSelectedRows()

    for (const row of selected) {
      try {
        await sendQuote(row.id, {
          price:       row.price  ?? 0,
          spread:      row.spread ?? 0,
          sent_price:  row.price  ?? 0,
          sent_spread: row.spread ?? 0,
          ref_source:  'TW',        // toolbar state not stored on row — use default
          convention:  'Price',
          skew:        0,
        })
        // Non-locking update: only stamp sent snapshot + QUOTED status.
        // All other fields (including ref prices) remain live.
        api.applyTransaction({
          update: [{
            ...row,
            status:     'QUOTED',
            sentPrice:  row.price,
            sentSpread: row.spread,
          }],
        })
      } catch (err) {
        console.error('SEND failed for inquiry', row.id, err)
      }
    }
  }, [])

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="blotter-root">
      <header className="blotter-header">
        <div className="blotter-brand">
          <span className="blotter-title">PT‑BLOTTER</span>
          <span className="blotter-subtitle">Fixed Income Bond Trading</span>
        </div>
        <div className="blotter-header-right">
          <span className="live-dot" aria-hidden="true">●</span>
          <span className="blotter-clock">{clock}</span>
        </div>
      </header>

      <Toolbar
        selectedCount={selectedCount}
        onApply={handleApply}
        onSend={handleSend}
      />

      <main className="blotter-main">
        <BlotterGrid
          onGridReady={handleGridReady}
          onSelectionChanged={handleSelectionChanged}
        />
      </main>
    </div>
  )
}
