const { createDomProbes } = require('../probes/dom-probes');

const ROWS = '.ag-center-cols-container';
const CELL_SEL = (col, row) =>
  `${ROWS} [row-index="${row}"] [col-id="${col}"]`;

function buildGrid(rows) {
  let html = `<div class="${ROWS.slice(1)}">`;
  rows.forEach(({ rowIndex, cols }) => {
    html += `<div row-index="${rowIndex}">`;
    cols.forEach(({ colId, text, cls }) => {
      html += `<div col-id="${colId}"${cls ? ` class="${cls}"` : ''}>${text}</div>`;
    });
    html += '</div>';
  });
  html += '</div>';
  return html;
}

describe('createDomProbes', () => {
  let probes;

  beforeEach(() => {
    document.body.innerHTML = buildGrid([
      { rowIndex: 0, cols: [{ colId: 'ticker', text: 'AAPL' }, { colId: 'instrument', text: 'Stock' }] },
      { rowIndex: 1, cols: [{ colId: 'ticker', text: 'MSFT' }, { colId: 'instrument', text: 'Stock' }] },
      { rowIndex: 2, cols: [{ colId: 'ticker', text: 'GOOG' }, { colId: 'instrument', text: 'ETF'   }] }
    ]);
    probes = createDomProbes(document);
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  describe('getVisibleCellTexts', () => {
    test('returns all cell texts for a column', () => {
      expect(probes.getVisibleCellTexts('ticker')).toEqual(['AAPL', 'MSFT', 'GOOG']);
    });
    test('returns empty array when column is not present', () => {
      expect(probes.getVisibleCellTexts('price')).toEqual([]);
    });
  });

  describe('getCellText', () => {
    test('returns trimmed text for a valid selector', () => {
      expect(probes.getCellText(CELL_SEL('ticker', 0))).toBe('AAPL');
    });
    test('returns null when selector matches nothing', () => {
      expect(probes.getCellText('.ag-center-cols-container [row-index="99"] [col-id="ticker"]')).toBeNull();
    });
  });

  describe('findRowIndexByText', () => {
    test('returns correct row-index for existing value', () => {
      expect(probes.findRowIndexByText('ticker', 'MSFT')).toBe(1);
    });
    test('returns -1 when value is not in visible rows', () => {
      expect(probes.findRowIndexByText('ticker', 'TSLA')).toBe(-1);
    });
  });

  describe('areAllVisibleCellsContaining', () => {
    test('returns true when all visible cells contain the text', () => {
      document.body.innerHTML = buildGrid([
        { rowIndex: 0, cols: [{ colId: 'instrument', text: 'Stock' }] },
        { rowIndex: 1, cols: [{ colId: 'instrument', text: 'Stock' }] }
      ]);
      probes = createDomProbes(document);
      expect(probes.areAllVisibleCellsContaining('instrument', 'Stock')).toBe(true);
    });
    test('returns false when any cell does not match', () => {
      expect(probes.areAllVisibleCellsContaining('instrument', 'Stock')).toBe(false);
    });
    test('returns false when column has no cells', () => {
      expect(probes.areAllVisibleCellsContaining('nonexistent', 'x')).toBe(false);
    });
    test('is case-insensitive', () => {
      document.body.innerHTML = buildGrid([
        { rowIndex: 0, cols: [{ colId: 'instrument', text: 'STOCK' }] }
      ]);
      probes = createDomProbes(document);
      expect(probes.areAllVisibleCellsContaining('instrument', 'stock')).toBe(true);
    });
  });

  describe('hasRowsInViewport', () => {
    test('returns true when rows exist', () => {
      expect(probes.hasRowsInViewport()).toBe(true);
    });
    test('returns false when container is empty', () => {
      document.body.innerHTML = `<div class="${ROWS.slice(1)}"></div>`;
      probes = createDomProbes(document);
      expect(probes.hasRowsInViewport()).toBe(false);
    });
  });

  describe('isRowInDom', () => {
    test('returns true for a rendered row-index', () => {
      expect(probes.isRowInDom(1)).toBe(true);
    });
    test('returns false for a row not in the DOM', () => {
      expect(probes.isRowInDom(99)).toBe(false);
    });
  });

  describe('getLastDomRowIndex', () => {
    test('returns the highest row-index present', () => {
      expect(probes.getLastDomRowIndex()).toBe(2);
    });
    test('returns -1 when no rows are rendered', () => {
      document.body.innerHTML = `<div class="${ROWS.slice(1)}"></div>`;
      probes = createDomProbes(document);
      expect(probes.getLastDomRowIndex()).toBe(-1);
    });
  });

  describe('isViewportAtBottom', () => {
    test('returns true when no viewport element exists', () => {
      expect(probes.isViewportAtBottom()).toBe(true);
    });
    test('returns true when scrolled to the bottom', () => {
      document.body.innerHTML += '<div class="ag-body-viewport"></div>';
      probes = createDomProbes(document);
      const vp = document.querySelector('.ag-body-viewport');
      Object.defineProperty(vp, 'scrollTop',    { value: 900, writable: true });
      Object.defineProperty(vp, 'clientHeight', { value: 100, writable: true });
      Object.defineProperty(vp, 'scrollHeight', { value: 1000, writable: true });
      expect(probes.isViewportAtBottom()).toBe(true);
    });
  });

  describe('isCellFlashing / isCellStable', () => {
    test('isCellFlashing returns true with ag-cell-data-changed class', () => {
      document.body.innerHTML = buildGrid([
        { rowIndex: 0, cols: [{ colId: 'price', text: '100', cls: 'ag-cell-data-changed' }] }
      ]);
      probes = createDomProbes(document);
      expect(probes.isCellFlashing(CELL_SEL('price', 0))).toBe(true);
    });
    test('isCellFlashing returns false without flash class', () => {
      expect(probes.isCellFlashing(CELL_SEL('ticker', 0))).toBe(false);
    });
    test('isCellFlashing returns false for missing element', () => {
      expect(probes.isCellFlashing('.nonexistent')).toBe(false);
    });
    test('isCellStable returns true without flash class', () => {
      expect(probes.isCellStable(CELL_SEL('ticker', 0))).toBe(true);
    });
    test('isCellStable returns false with ag-cell-data-changed class', () => {
      document.body.innerHTML = buildGrid([
        { rowIndex: 0, cols: [{ colId: 'price', text: '100', cls: 'ag-cell-data-changed' }] }
      ]);
      probes = createDomProbes(document);
      expect(probes.isCellStable(CELL_SEL('price', 0))).toBe(false);
    });
  });

  describe('hasCellValueChanged', () => {
    test('returns true when text differs from initial value', () => {
      expect(probes.hasCellValueChanged(CELL_SEL('ticker', 0), 'OLD')).toBe(true);
    });
    test('returns false when text equals initial value', () => {
      expect(probes.hasCellValueChanged(CELL_SEL('ticker', 0), 'AAPL')).toBe(false);
    });
    test('returns false for empty cell text', () => {
      document.body.innerHTML = buildGrid([
        { rowIndex: 0, cols: [{ colId: 'price', text: '' }] }
      ]);
      probes = createDomProbes(document);
      expect(probes.hasCellValueChanged(CELL_SEL('price', 0), 'anything')).toBe(false);
    });
    test('returns false when element is not found', () => {
      expect(probes.hasCellValueChanged('.nonexistent', 'x')).toBe(false);
    });
  });
});
