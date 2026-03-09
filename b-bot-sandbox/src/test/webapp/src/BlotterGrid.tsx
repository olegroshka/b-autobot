import { useRef, useState, useCallback, useEffect } from 'react'
import { AgGridReact } from 'ag-grid-react'
import {
  type ColDef,
  type ColGroupDef,
  type GridReadyEvent,
  type SelectionChangedEvent,
  type CellValueChangedEvent,
  type CellDoubleClickedEvent,
  type GridApi,
  AllCommunityModule,
  ModuleRegistry,
  themeBalham,
} from 'ag-grid-community'
import type { Inquiry } from './types'
import { buildSeedInquiries } from './seedData'
import { initSimulator, startSimulator, stopSimulator, addRowToSimulator } from './PriceSimulator'
import { DOUBLE_CLICK_FILTER_COLS } from './filterUtils'
import './BlotterGrid.css'

// Register all AG Grid Community modules once at module load.
// Required in AG Grid v33+.
ModuleRegistry.registerModules([AllCommunityModule])

// ── Dark theme (AG Grid v33 Theming API) ──────────────────────────────────────
//
// AG Grid v33 uses a JavaScript-based theming system.  Passing a `theme` prop
// to <AgGridReact> is required; otherwise the grid ignores any `ag-theme-*`
// CSS class and renders with its built-in light Quartz defaults.
//
const darkTheme = themeBalham.withParams({
  accentColor:                '#1e90ff',
  backgroundColor:            '#06090f',
  borderColor:                '#182436',
  browserColorScheme:         'dark',
  fontFamily:                 "'Segoe UI', system-ui, sans-serif",
  fontSize:                   12,
  foregroundColor:            '#bccedd',
  headerBackgroundColor:      '#0b0f18',
  headerTextColor:            '#466070',
  rowHeight:                  28,
  headerHeight:               30,
  oddRowBackgroundColor:      '#090d15',
  rowHoverColor:              'rgba(21, 101, 192, 0.13)',
  selectedRowBackgroundColor: 'rgba(21, 101, 192, 0.30)',
  chromeBackgroundColor:      '#0b0f18',
})

// ── Column definitions ────────────────────────────────────────────────────────
//
// Every colId is taken verbatim from the design contract in BLOTTER_DESIGN.md.
// These same values appear in Cucumber feature files as quoted strings, so
// changing a colId here requires matching changes in BondBlotter.feature and
// BondBlotterSteps.java.
//
// Skew controls (ref source, ref side, markup, units) are toolbar-only and
// intentionally NOT represented as grid columns.

const COLUMN_DEFS: (ColDef<Inquiry> | ColGroupDef<Inquiry>)[] = [

  // ── Identity group ──────────────────────────────────────────────────────────
  {
    headerName: 'Identity',
    children: [
      {
        colId: 'ptId',
        headerName: 'Portfolio',
        field: 'ptId',
        width: 155,
        pinned: 'left',
        filter: 'agTextColumnFilter',
      },
      {
        colId: 'ptLineId',
        headerName: '#',
        field: 'ptLineId',
        width: 44,
        pinned: 'left',
        valueFormatter: (p) => p.value?.split('_').pop() ?? '',
      },
      { colId: 'isin',        headerName: 'ISIN',        field: 'isin',        width: 130, pinned: 'left', filter: 'agTextColumnFilter' },
      { colId: 'description', headerName: 'Description', field: 'description', width: 200, minWidth: 120, filter: 'agTextColumnFilter' },
      { colId: 'maturity',    headerName: 'Maturity',    field: 'maturity',    width: 100, filter: 'agTextColumnFilter' },
      {
        colId: 'coupon',
        headerName: 'Coupon',
        field: 'coupon',
        width: 75,
        valueFormatter: (p) => p.value != null ? p.value.toFixed(3) + '%' : '',
      },
      {
        colId: 'notional',
        headerName: 'Notional',
        field: 'notional',
        width: 120,
        valueFormatter: (p) =>
          p.value != null ? (p.value as number).toLocaleString('en-US') : '',
      },
      { colId: 'side',   headerName: 'Side',   field: 'side',   width: 60,   filter: 'agTextColumnFilter',
        cellClassRules: { 'side-buy': (p) => p.value === 'BUY', 'side-sell': (p) => p.value === 'SELL' } },
      { colId: 'client', headerName: 'Client', field: 'client', width: 100, filter: 'agTextColumnFilter' },
    ],
  },

  // ── Status ──────────────────────────────────────────────────────────────────
  {
    headerName: 'Status',
    children: [
      {
        colId: 'status',
        headerName: 'Status',
        field: 'status',
        width: 90,
        filter: 'agTextColumnFilter',
        cellClassRules: {
          'status-pending': (p) => p.value === 'PENDING',
          'status-quoted':  (p) => p.value === 'QUOTED',
          'status-done':    (p) => p.value === 'DONE',
          'status-missed':  (p) => p.value === 'MISSED',
        },
      },
    ],
  },

  // ── TradeWeb reference prices ────────────────────────────────────────────────
  {
    headerName: 'TradeWeb',
    children: [
      { colId: 'twPrice',  headerName: 'TW Price',  field: 'twPrice',  width: 150, enableCellChangeFlash: true },
      { colId: 'twSpread', headerName: 'TW Spread', field: 'twSpread', width: 140, enableCellChangeFlash: true },
    ],
  },

  // ── CP+ reference prices ─────────────────────────────────────────────────────
  {
    headerName: 'CP+',
    children: [
      { colId: 'cpPrice',  headerName: 'CP+ Price',  field: 'cpPrice',  width: 150, enableCellChangeFlash: true },
      { colId: 'cpSpread', headerName: 'CP+ Spread', field: 'cpSpread', width: 140, enableCellChangeFlash: true },
    ],
  },

  // ── CBBT reference prices ────────────────────────────────────────────────────
  {
    headerName: 'CBBT',
    children: [
      { colId: 'cbbPrice',  headerName: 'CBBT Price',  field: 'cbbPrice',  width: 150, enableCellChangeFlash: true },
      { colId: 'cbbSpread', headerName: 'CBBT Spread', field: 'cbbSpread', width: 140, enableCellChangeFlash: true },
    ],
  },

  // ── Applied values ────────────────────────────────────────────────────────────
  {
    headerName: 'Applied',
    children: [
      {
        colId: 'pricingAction',
        headerName: 'Pricing Action',
        field: 'pricingAction',
        width: 140,
        cellClassRules: {
          'pricing-pos': (p) => typeof p.value === 'string' && p.value.includes('+'),
          'pricing-neg': (p) => typeof p.value === 'string' && p.value.includes('-'),
          'pricing-label': (p) => p.value != null,
        },
      },
      {
        colId: 'price', headerName: 'Price', field: 'price', width: 95,
        valueFormatter: (p) => p.value != null ? (p.value as number).toFixed(4) : '',
        editable: true,
        valueSetter: (params) => {
          const v = parseFloat(String(params.newValue))
          if (isNaN(v)) return false
          params.data.price = v
          params.data.appliedConfig = undefined
          params.data.pricingAction = 'Price input'
          return true
        },
      },
      {
        colId: 'spread', headerName: 'Spread', field: 'spread', width: 95,
        valueFormatter: (p) => p.value != null ? (p.value as number).toFixed(2) : '',
        editable: true,
        valueSetter: (params) => {
          const v = parseFloat(String(params.newValue))
          if (isNaN(v)) return false
          params.data.spread = v
          params.data.appliedConfig = undefined
          params.data.pricingAction = 'Spread input'
          return true
        },
      },
    ],
  },

  // ── Sent snapshot ─────────────────────────────────────────────────────────────
  {
    headerName: 'Sent',
    children: [
      { colId: 'quotedPrice',  headerName: 'Quoted Price',  field: 'quotedPrice',  width: 95,
        valueFormatter: (p) => p.value != null ? (p.value as number).toFixed(4) : '' },
      { colId: 'quotedSpread', headerName: 'Quoted Spread', field: 'quotedSpread', width: 95,
        valueFormatter: (p) => p.value != null ? (p.value as number).toFixed(2) : '' },
    ],
  },
]

const DEFAULT_COL_DEF: ColDef = {
  resizable: true,
  sortable: true,
  suppressHeaderMenuButton: false,
}

// ── Props ─────────────────────────────────────────────────────────────────────

interface BlotterGridProps {
  /** Called once when the AG Grid API is ready. App uses this to issue APPLY/SEND transactions. */
  onGridReady?: (api: GridApi<Inquiry>) => void
  /** Called whenever row selection changes; receives the new selected-row count. */
  onSelectionChanged?: (count: number) => void
  /** Called when the user double-clicks a stable (non-ticking, non-editable) cell. */
  onDoubleClickFilter?: (colId: string, value: string) => void
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function BlotterGrid({ onGridReady: onGridReadyProp, onSelectionChanged, onDoubleClickFilter }: BlotterGridProps) {
  const gridRef   = useRef<AgGridReact<Inquiry>>(null)
  const apiRef    = useRef<GridApi<Inquiry> | null>(null)
  const [rowData] = useState<Inquiry[]>(() => buildSeedInquiries())

  const onGridReady = useCallback((event: GridReadyEvent<Inquiry>) => {
    apiRef.current = event.api
    initSimulator(rowData)
    startSimulator(event.api)
    onGridReadyProp?.(event.api)

    // Fetch any dynamic inquiries submitted via the REST API (not in hardcoded seed)
    // and add them to the grid so cancelled/quoted rows submitted externally are visible.
    fetch('/api/inquiries')
      .then(r => r.ok ? r.json() : [])
      .then((serverRows: any[]) => {
        const existingIds = new Set(rowData.map(r => r.id))
        const toAdd = serverRows
          .filter(r => !existingIds.has(r.inquiry_id))
          .map(r => ({
            id:          r.inquiry_id,
            ptId:        r.pt_id        ?? '',
            ptLineId:    r.pt_line_id   ?? '',
            isin:        r.isin         ?? '',
            description: r.description  ?? '',
            maturity:    r.maturity     ?? '',
            coupon:      r.coupon       ?? 0,
            notional:    r.notional     ?? 0,
            side:        r.side         ?? 'BUY',
            client:      r.client       ?? '',
            status:      r.status       ?? 'PENDING',
            twPrice:     r.twPrice      ?? '',
            twSpread:    r.twSpread     ?? '',
            cpPrice:     r.cpPrice      ?? '',
            cpSpread:    r.cpSpread     ?? '',
            cbbPrice:    r.cbbPrice     ?? '',
            cbbSpread:   r.cbbSpread    ?? '',
            pricingAction: null, price: null, spread: null, quotedPrice: null, quotedSpread: null,
          } as Inquiry))
        if (toAdd.length > 0) {
          event.api.applyTransaction({ add: toAdd })
          toAdd.forEach(addRowToSimulator)
        }
      })
      .catch(() => { /* API not reachable — use seed-only data */ })
  }, [rowData, onGridReadyProp])

  const handleSelectionChanged = useCallback((event: SelectionChangedEvent<Inquiry>) => {
    onSelectionChanged?.(event.api.getSelectedRows().length)
  }, [onSelectionChanged])

  // After a manual price/spread edit, refresh the pricingAction cell in the same row
  const handleCellValueChanged = useCallback((event: CellValueChangedEvent<Inquiry>) => {
    const col = event.column.getColId()
    if (col === 'price' || col === 'spread') {
      event.api.refreshCells({ rowNodes: [event.node], columns: ['pricingAction'], force: true })
    }
  }, [])

  // Double-click on a stable column → propagate to App for auto-filter
  const handleCellDoubleClicked = useCallback((event: CellDoubleClickedEvent<Inquiry>) => {
    if (!onDoubleClickFilter) return
    const colId = event.column.getColId()
    if (!DOUBLE_CLICK_FILTER_COLS.has(colId)) return
    const value = String(event.value ?? '')
    if (!value) return
    onDoubleClickFilter(colId, value)
  }, [onDoubleClickFilter])

  useEffect(() => {
    return () => stopSimulator()
  }, [])

  return (
    <div className="blotter-grid">
      <AgGridReact<Inquiry>
        ref={gridRef}
        theme={darkTheme}
        rowData={rowData}
        columnDefs={COLUMN_DEFS}
        defaultColDef={DEFAULT_COL_DEF}
        getRowId={(params) => params.data.id}
        rowSelection={{ mode: 'multiRow', checkboxes: false, headerCheckbox: false, enableClickSelection: true }}
        suppressColumnVirtualisation={true}
        animateRows={true}
        onGridReady={onGridReady}
        onSelectionChanged={handleSelectionChanged}
        onCellValueChanged={handleCellValueChanged}
        onCellDoubleClicked={handleCellDoubleClicked}
      />
    </div>
  )
}
