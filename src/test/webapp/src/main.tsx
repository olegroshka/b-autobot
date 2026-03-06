import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// Note: do NOT import ag-grid.css or ag-theme-*.css here.
// The AG Grid v33 Theming API (themeBalham.withParams) in BlotterGrid.tsx
// injects all grid styles via JavaScript — legacy CSS imports cause error #106.
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
