import './App.css'

/**
 * PT-Blotter root component.
 *
 * M0: minimal shell — page title and header only.
 * M1: BlotterGrid with AG Grid column definitions and seeded data.
 */
export default function App() {
  return (
    <div className="blotter-root">
      <header className="blotter-header">
        <span className="blotter-title">PT-Blotter</span>
        <span className="blotter-subtitle">Fixed Income Bond Trading</span>
      </header>
      <main className="blotter-main">
        <div id="grid-container" className="blotter-grid-placeholder">
          {/* BlotterGrid will render here from M1 onwards */}
          AG Grid will render here
        </div>
      </main>
    </div>
  )
}
