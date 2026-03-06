import { useState } from 'react'
import type { RefSource, RefSide, Units } from './types'
import './Toolbar.css'

interface ToolbarProps {
  selectedCount: number
  onApply: (refSource: RefSource, refSide: RefSide, markup: number, units: Units) => void
  onSend: () => void
}

/** Increment steps per unit mode. */
const STEP: Record<Units, number> = { c: 0.01, bp: 1 }

export default function Toolbar({ selectedCount, onApply, onSend }: ToolbarProps) {
  const [refSource, setRefSource] = useState<RefSource>('TW')
  const [refSide,   setRefSide]   = useState<RefSide>('Mid')
  const [markup,    setMarkup]    = useState(0)
  const [units,     setUnits]     = useState<Units>('c')

  const step = STEP[units]

  const decrement = () => setMarkup(m => parseFloat((m - step).toFixed(6)))
  const increment = () => setMarkup(m => parseFloat((m + step).toFixed(6)))

  const handleMarkupChange = (raw: string) => {
    const parsed = parseFloat(raw)
    setMarkup(isNaN(parsed) ? 0 : parsed)
  }

  return (
    <div className="blotter-toolbar" role="toolbar" aria-label="Blotter Controls">

      {/* ── Ref Source ─────────────────────────────────────────────────────── */}
      <div className="toolbar-group">
        <span className="toolbar-label">Source</span>
        <select
          aria-label="Ref Source"
          value={refSource}
          onChange={(e) => setRefSource(e.target.value as RefSource)}
        >
          <option value="TW">TW</option>
          <option value="CP+">CP+</option>
          <option value="CBBT">CBBT</option>
        </select>
      </div>

      <div className="toolbar-sep" aria-hidden="true" />

      {/* ── Ref Side ───────────────────────────────────────────────────────── */}
      <div className="toolbar-group">
        <span className="toolbar-label">Side</span>
        <select
          aria-label="Ref Side"
          value={refSide}
          onChange={(e) => setRefSide(e.target.value as RefSide)}
        >
          <option value="Bid">Bid</option>
          <option value="Ask">Ask</option>
          <option value="Mid">Mid</option>
        </select>
      </div>

      <div className="toolbar-sep" aria-hidden="true" />

      {/* ── Markup ─────────────────────────────────────────────────────────── */}
      <div className="toolbar-group">
        <span className="toolbar-label">Markup</span>
        <div className="markup-control">
          <button
            aria-label="Decrease Markup"
            className="markup-btn"
            onClick={decrement}
            type="button"
          >−</button>
          <input
            aria-label="Markup Value"
            type="number"
            step={step}
            value={markup}
            onChange={(e) => handleMarkupChange(e.target.value)}
            className="markup-input"
          />
          <button
            aria-label="Increase Markup"
            className="markup-btn"
            onClick={increment}
            type="button"
          >+</button>
        </div>
      </div>

      <div className="toolbar-sep" aria-hidden="true" />

      {/* ── Units ──────────────────────────────────────────────────────────── */}
      <div className="toolbar-group" role="group" aria-label="Units">
        <span className="toolbar-label">Units</span>
        <div className="units-toggle">
          <button
            aria-label="Units c"
            type="button"
            className={`units-btn${units === 'c' ? ' active' : ''}`}
            onClick={() => setUnits('c')}
          >c</button>
          <button
            aria-label="Units bp"
            type="button"
            className={`units-btn${units === 'bp' ? ' active' : ''}`}
            onClick={() => setUnits('bp')}
          >bp</button>
        </div>
      </div>

      {/* ── Selection count ────────────────────────────────────────────────── */}
      <span
        className={`selection-badge${selectedCount > 0 ? ' has-selection' : ''}`}
        aria-label="Selection Count"
      >
        {selectedCount} selected
      </span>

      {/* ── Action buttons ─────────────────────────────────────────────────── */}
      <button
        aria-label="Apply"
        type="button"
        onClick={() => onApply(refSource, refSide, markup, units)}
        disabled={selectedCount === 0}
        className="toolbar-btn apply-btn"
      >APPLY</button>

      <button
        aria-label="Send"
        type="button"
        onClick={onSend}
        disabled={selectedCount === 0}
        className="toolbar-btn send-btn"
      >SEND</button>

    </div>
  )
}
