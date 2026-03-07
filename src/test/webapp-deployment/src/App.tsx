import { useRef, useState, useCallback, useEffect } from 'react'
import { AgGridReact } from 'ag-grid-react'
import {
  type ColDef,
  AllCommunityModule,
  ModuleRegistry,
  themeBalham,
} from 'ag-grid-community'
import type { Deployment, ServiceStatus } from './types'
import { fetchDeployments } from './api'
import './App.css'

ModuleRegistry.registerModules([AllCommunityModule])

// ── Dark theme (AG Grid v33 Theming API) ─────────────────────────────────────
const darkTheme = themeBalham.withParams({
  accentColor:                '#6366f1',
  backgroundColor:            '#0b0f1a',
  borderColor:                '#1e293b',
  browserColorScheme:         'dark',
  fontFamily:                 "'Segoe UI', system-ui, sans-serif",
  fontSize:                   12,
  foregroundColor:            '#cbd5e1',
  headerBackgroundColor:      '#0f172a',
  headerTextColor:            '#64748b',
  rowHeight:                  30,
  headerHeight:               32,
  oddRowBackgroundColor:      '#090d15',
  rowHoverColor:              'rgba(99, 102, 241, 0.08)',
  selectedRowBackgroundColor: 'rgba(99, 102, 241, 0.20)',
  chromeBackgroundColor:      '#0f172a',
})

// ── Status cell renderer ──────────────────────────────────────────────────────
const STATUS_COLORS: Record<ServiceStatus, string> = {
  RUNNING: '#4ade80',
  STOPPED: '#94a3b8',
  FAILED:  '#f87171',
}

function StatusCell({ value }: { value: ServiceStatus }) {
  const color = STATUS_COLORS[value] ?? '#e2e8f0'
  return (
    <span style={{ color, fontWeight: 600, letterSpacing: '0.04em' }}>
      ● {value}
    </span>
  )
}

// ── Column definitions ────────────────────────────────────────────────────────
const COL_DEFS: ColDef<Deployment>[] = [
  {
    colId: 'name',
    headerName: 'Service',
    field: 'name',
    flex: 2,
    sort: 'asc',
    cellStyle: { fontFamily: "'Consolas', 'Cascadia Code', monospace", fontSize: 11 },
  },
  {
    colId: 'version',
    headerName: 'Version',
    field: 'version',
    width: 100,
    cellStyle: { fontFamily: "'Consolas', 'Cascadia Code', monospace", color: '#a5b4fc' },
  },
  {
    colId: 'status',
    headerName: 'Status',
    field: 'status',
    width: 130,
    cellRenderer: StatusCell,
  },
  {
    colId: 'environment',
    headerName: 'Env',
    field: 'environment',
    width: 70,
    cellStyle: { color: '#fbbf24', fontWeight: 600 },
  },
  {
    colId: 'host',
    headerName: 'Host',
    field: 'host',
    flex: 1.5,
    cellStyle: { color: '#94a3b8' },
  },
  {
    colId: 'port',
    headerName: 'Port',
    field: 'port',
    width: 72,
    cellStyle: { color: '#94a3b8' },
  },
  {
    colId: 'team',
    headerName: 'Team',
    field: 'team',
    flex: 1.5,
  },
  {
    colId: 'lastDeployed',
    headerName: 'Last Deployed',
    field: 'lastDeployed',
    flex: 1.5,
    valueFormatter: p =>
      p.value ? new Date(p.value as string).toLocaleString('en-GB', { dateStyle: 'short', timeStyle: 'short' }) : '',
  },
  {
    colId: 'build',
    headerName: 'Build',
    field: 'build',
    width: 110,
    cellStyle: { color: '#64748b', fontSize: 11 },
  },
  {
    colId: 'uptime',
    headerName: 'Uptime',
    field: 'uptime',
    width: 95,
    cellStyle: (p) => ({
      color: (p.value as string) === '-' ? '#f87171' : '#34d399',
    }),
  },
]

// ── Root component ────────────────────────────────────────────────────────────
export default function App() {
  const gridRef      = useRef<AgGridReact<Deployment>>(null)
  const [rows,       setRows]       = useState<Deployment[]>([])
  const [error,      setError]      = useState<string | null>(null)
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null)

  const load = useCallback(async () => {
    try {
      const data = await fetchDeployments()
      setRows(data)
      setLastUpdate(new Date())
      setError(null)
    } catch (e) {
      setError(String(e))
    }
  }, [])

  useEffect(() => { load() }, [load])

  const onFilterChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    gridRef.current?.api?.setGridOption('quickFilterText', e.target.value)
  }

  const running = rows.filter(r => r.status === 'RUNNING').length
  const stopped = rows.filter(r => r.status === 'STOPPED').length
  const failed  = rows.filter(r => r.status === 'FAILED').length

  return (
    <div className="dd-root">

      <header className="dd-header">
        <span className="dd-title">DEPLOYMENT DASHBOARD</span>
        <span className="dd-env-badge">UAT</span>
        <div className="dd-counts">
          <span className="dd-count dd-running">{running} RUNNING</span>
          <span className="dd-count dd-stopped">{stopped} STOPPED</span>
          <span className="dd-count dd-failed">{failed} FAILED</span>
        </div>
        <span className="dd-updated">
          {lastUpdate ? `Updated ${lastUpdate.toLocaleTimeString()}` : ''}
        </span>
        <button className="dd-refresh" onClick={load} aria-label="Refresh">
          ↺ Refresh
        </button>
      </header>

      {error && (
        <div className="dd-error">
          {error}
          <button onClick={() => setError(null)}>×</button>
        </div>
      )}

      <div className="dd-toolbar">
        <input
          type="text"
          placeholder="Filter services…"
          aria-label="Filter services"
          className="dd-filter"
          onChange={onFilterChange}
        />
        <span className="dd-total">{rows.length} services</span>
      </div>

      <div className="dd-grid">
        <AgGridReact<Deployment>
          ref={gridRef}
          theme={darkTheme}
          rowData={rows}
          columnDefs={COL_DEFS}
          suppressColumnVirtualisation={true}
          rowSelection={{ mode: 'singleRow', checkboxes: false }}
          animateRows={true}
        />
      </div>

    </div>
  )
}
