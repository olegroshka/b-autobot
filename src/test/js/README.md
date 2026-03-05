# AG Grid Probes — JavaScript Unit Tests

Self-contained npm workspace that tests the browser probe modules in isolation
using **Jest + jsdom** — no browser, no Playwright, no Maven required.

## Prerequisites

Node.js 18+ (LTS).  Install from https://nodejs.org if not already present.

## Install

```bash
cd src/test/js
npm install
```

## Run tests

```bash
# All probes
npm test

# Watch mode (re-runs on save)
npm run test:watch

# Coverage report
npm run test:coverage
```

## Probe modules

| File | Responsibility |
|---|---|
| `probes/api-discovery.js` | Locates AG Grid API via React fibre / window globals |
| `probes/dom-probes.js` | Pure DOM queries — visible cells, row indices, flash state |
| `probes/grid-api-probes.js` | AG Grid data model search + ensureIndexVisible |
| `probes/filter-probes.js` | Apply / clear filter models (text + set filter) |
| `probes/scroll-probes.js` | Viewport scroll operations |
| `probes/ticking-probes.js` | Live cell update detection |
| `probes/bundle.js` | Browser IIFE — registers `window.agGridProbes` namespace |

## Design

Each module exports a **factory function** that accepts `(doc, win)` as dependencies.
This lets Jest inject a jsdom `document` / `window` instead of a real browser,
making every probe fully testable without Playwright.

```js
// Node / Jest
const { createDomProbes } = require('./probes/dom-probes');
const probes = createDomProbes(document);   // jsdom document
probes.getVisibleCellTexts('ticker');       // → ['AAPL', 'MSFT', …]

// Browser (via bundle)
window.agGridProbes.dom.getVisibleCellTexts('ticker');
```

The `bundle.js` IIFE is injected into every Playwright browser context via
`BrowserContext.addInitScript()` in `PlaywrightManager.initContext()`.  Keep
`bundle.js` in sync with the individual probe files when making changes.
