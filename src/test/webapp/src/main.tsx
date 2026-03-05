import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// AG Grid CSS — core structure + Balham Dark theme
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-balham.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
