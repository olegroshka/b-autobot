import BlotterGrid from './BlotterGrid'
import './App.css'

/**
 * PT-Blotter root component.
 *
 * M0: minimal shell (page title + header).
 * M1: BlotterGrid renders AG Grid with all design-contract columns + seeded data.
 * M2: PriceSimulator will tick ref price cells.
 * M3: toolbar buttons (APPLY / SEND) + REST inquiry ingestion.
 */
export default function App() {
  return (
    <div className="blotter-root">
      <header className="blotter-header">
        <span className="blotter-title">PT-Blotter</span>
        <span className="blotter-subtitle">Fixed Income Bond Trading</span>
      </header>
      <main className="blotter-main">
        <BlotterGrid />
      </main>
    </div>
  )
}
