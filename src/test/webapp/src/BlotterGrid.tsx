import { useRef, useState, useCallback, useEffect } from 'react'
import { AgGridReact } from 'ag-grid-react'
import {
  type ColDef,
  type ColGroupDef,
  type GridReadyEvent,
  type GridApi,
  AllCommunityModule,
  ModuleRegistry,
} from 'ag-grid-community'
import type { Inquiry } from './types'
import { buildSeedInquiries } from './seedData'
import { initSimulator, startSimulator, stopSimulator } from './PriceSimulator'
import './BlotterGrid.css'

// Register all AG Grid Community modules once at module load.
// Required in AG Grid v33+.
ModuleRegistry.registerModules([AllCommunityModule])

// ── Column definitions ────────────────────────────────────────────────────────
//
// Every colId is taken verbatim from the design contract in BLOTTER_DESIGN.md.
// These same values appear in Cucumber feature files as quoted strings, so
// changing a colId here requires matching changes in BondBlotter.feature and
// BondBlotterSteps.java.

const COLUMN_DEFS: (ColDef<Inquiry> | ColGroupDef<Inquiry>)[] = [

  // ── Identity group ──────────────────────────────────────────────────────────
  {
    headerName: 'Identity',
    children: [
      {
        colId: 'select',
        headerName: '',
        checkboxSelection: true,
        headerCheckboxSelection: true,
        width: 40,
        resizable: false,
        suppressHeaderMenuButton: true,
        pinned: 'left',
      },
      { colId: 'isin',        headerName: 'ISIN',        field: 'isin',        width: 130, pinned: 'left' },
      { colId: 'description', headerName: 'Description', field: 'description', width: 200, minWidth: 120 },
      { colId: 'maturity',    headerName: 'Maturity',    field: 'maturity',    width: 100 },
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
      { colId: 'side',   headerName: 'Side',   field: 'side',   width: 60,
        cellClassRules: { 'side-buy': (p) => p.value === 'BUY', 'side-sell': (p) => p.value === 'SELL' } },
      { colId: 'client', headerName: 'Client', field: 'client', width: 100 },
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

  // ── Skew controls ────────────────────────────────────────────────────────────
  {
    headerName: 'Skew Controls',
    children: [
      { colId: 'refSource',  headerName: 'Ref Source',  field: 'refSource',  width: 95,
        valueFormatter: (p) => p.value ?? '' },
      { colId: 'convention', headerName: 'Convention',  field: 'convention', width: 95,
        valueFormatter: (p) => p.value ?? '' },
      { colId: 'skewDelta',  headerName: 'Skew Δ',      field: 'skewDelta',  width: 80,
        valueFormatter: (p) => p.value != null ? String(p.value) : '' },
    ],
  },

  // ── Applied values ────────────────────────────────────────────────────────────
  {
    headerName: 'Applied',
    children: [
      { colId: 'price',  headerName: 'Price',  field: 'price',  width: 95,
        valueFormatter: (p) => p.value != null ? (p.value as number).toFixed(4) : '' },
      { colId: 'spread', headerName: 'Spread', field: 'spread', width: 95,
        valueFormatter: (p) => p.value != null ? (p.value as number).toFixed(2) : '' },
    ],
  },

  // ── Sent snapshot ─────────────────────────────────────────────────────────────
  {
    headerName: 'Sent',
    children: [
      { colId: 'sentPrice',  headerName: 'Sent Price',  field: 'sentPrice',  width: 95,
        valueFormatter: (p) => p.value != null ? (p.value as number).toFixed(4) : '' },
      { colId: 'sentSpread', headerName: 'Sent Spread', field: 'sentSpread', width: 95,
        valueFormatter: (p) => p.value != null ? (p.value as number).toFixed(2) : '' },
    ],
  },
]

const DEFAULT_COL_DEF: ColDef = {
  resizable: true,
  sortable: true,
  suppressHeaderMenuButton: false,
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function BlotterGrid() {
  const gridRef   = useRef<AgGridReact<Inquiry>>(null)
  const apiRef    = useRef<GridApi<Inquiry> | null>(null)
  const [rowData] = useState<Inquiry[]>(() => buildSeedInquiries())

  // Start price simulator when grid is ready; stop on unmount.
  const onGridReady = useCallback((event: GridReadyEvent<Inquiry>) => {
    apiRef.current = event.api
    initSimulator(rowData)
    startSimulator(event.api)
    // Future: fetch live inquiries from /api/inquiries here (M3)
  }, [rowData])

  useEffect(() => {
    return () => stopSimulator()
  }, [])

  return (
    <div className="blotter-grid ag-theme-balham-dark">
      <AgGridReact<Inquiry>
        ref={gridRef}
        rowData={rowData}
        columnDefs={COLUMN_DEFS}
        defaultColDef={DEFAULT_COL_DEF}
        rowSelection="multiple"
        animateRows={true}
        enableCellChangeFlash={true}
        onGridReady={onGridReady}
      />
    </div>
  )
}
