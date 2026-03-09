import { useRef, useState, useCallback, useEffect } from 'react'
import type { GridApi } from 'ag-grid-community'
import type { Inquiry, RefSource, RefSide, Units } from './types'
// RefSource/RefSide/Units are used by the Toolbar callback signature below
import BlotterGrid from './BlotterGrid'
import Toolbar from './Toolbar'
import { sendQuote, releasePt } from './api'
import { computeApplied, formatPricingAction } from './priceUtils'
import { applyGridFilter, formatFilterText } from './filterUtils'
import './App.css'

// ── Root component ────────────────────────────────────────────────────────────

/**
 * PT-Blotter root component.
 *
 * Owns the AG Grid API reference and drives the toolbar → APPLY / SEND workflow:
 *   - APPLY: reads toolbar settings, computes price (c) or spread (bp) for each
 *             selected row and pushes via synchronous applyTransaction.
 *   - SEND:  POSTs a quote for each selected row, then stamps status=QUOTED and
 *             captures the quotedPrice/quotedSpread snapshot.  Non-locking — the row
 *             stays fully editable for re-quote (re-APPLY → re-SEND) cycles.
 */
export default function App() {
  const gridApiRef    = useRef<GridApi<Inquiry> | null>(null)
  const [selectedCount, setSelectedCount] = useState(0)
  const [filterText,    setFilterText]    = useState('')
  const [clock, setClock] = useState('')
  const [isPTAdmin, setIsPTAdmin] = useState(false)

  // Read user and configUrl from URL params
  const searchParams = new URLSearchParams(window.location.search)
  const user      = searchParams.get('user')      ?? 'trader'
  const configUrl = searchParams.get('configUrl') ?? ''

  useEffect(() => {
    const tick = () => setClock(new Date().toLocaleTimeString('en-GB', { hour12: false }))
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [])

  // Fetch isPTAdmin from config service on mount
  useEffect(() => {
    const url = `${configUrl}/api/config/credit.pt.access/Permissions/${user}`
    fetch(url)
      .then(r => r.ok ? r.json() : null)
      .then((data: { isPTAdmin?: boolean } | null) => {
        if (data && typeof data.isPTAdmin === 'boolean') {
          setIsPTAdmin(data.isPTAdmin)
        }
      })
      .catch(() => { /* config service unavailable — default to non-admin */ })
  }, [user, configUrl])

  const handleGridReady = useCallback((api: GridApi<Inquiry>) => {
    gridApiRef.current = api
  }, [])

  const handleSelectionChanged = useCallback((count: number) => {
    setSelectedCount(count)
  }, [])

  // ── Filter ────────────────────────────────────────────────────────────────

  const handleFilterChange = useCallback((text: string) => {
    setFilterText(text)
    const api = gridApiRef.current
    if (api) applyGridFilter(api, text)
  }, [])

  const handleDoubleClickFilter = useCallback((colId: string, value: string) => {
    const text = formatFilterText(colId, value)
    setFilterText(text)
    const api = gridApiRef.current
    if (api) applyGridFilter(api, text)
  }, [])

  // ── APPLY ─────────────────────────────────────────────────────────────────

  const handleApply = useCallback(
    (refSource: RefSource, refSide: RefSide, markup: number, units: Units) => {
      const api = gridApiRef.current
      if (!api) return

      const config = { refSource, refSide, markup, units }
      const updates: Inquiry[] = api.getSelectedRows().map((row) => {
        const { price, spread } = computeApplied(row, config)
        return {
          ...row,
          pricingAction: formatPricingAction(config),
          price,
          spread,
          appliedConfig: config,
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
            status:       'QUOTED',
            quotedPrice:  row.price,
            quotedSpread: row.spread,
          }],
        })
      } catch (err) {
        console.error('SEND failed for inquiry', row.id, err)
      }
    }
  }, [])

  // ── RELEASE PT ────────────────────────────────────────────────────────────

  const handleReleasePt = useCallback(async () => {
    const api = gridApiRef.current
    if (!api) return

    const selected = api.getSelectedRows()

    for (const row of selected) {
      try {
        await releasePt(row.id)
        api.applyTransaction({
          update: [{ ...row, status: 'RELEASED' }],
        })
      } catch (err) {
        console.error('RELEASE PT failed for inquiry', row.id, err)
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
        filterText={filterText}
        onFilterChange={handleFilterChange}
        isReleasePtEnabled={isPTAdmin}
        onReleasePt={handleReleasePt}
      />

      <main className="blotter-main">
        <BlotterGrid
          onGridReady={handleGridReady}
          onSelectionChanged={handleSelectionChanged}
          onDoubleClickFilter={handleDoubleClickFilter}
        />
      </main>
    </div>
  )
}
