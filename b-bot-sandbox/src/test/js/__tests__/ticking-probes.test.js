const { createTickingProbes } = require('../probes/ticking-probes');

const SEL = '.ag-center-cols-container [row-index="0"] [col-id="price"]';

function buildCell(text, cls) {
  const classAttr = cls ? ` class="${cls}"` : '';
  document.body.innerHTML = `
    <div class="ag-center-cols-container">
      <div row-index="0">
        <div col-id="price"${classAttr}>${text}</div>
      </div>
    </div>`;
}

describe('createTickingProbes', () => {
  let probes;

  beforeEach(() => {
    probes = createTickingProbes(document);
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  describe('isCellFlashing', () => {
    test('returns true when cell has ag-cell-data-changed class', () => {
      buildCell('100', 'ag-cell-data-changed');
      expect(probes.isCellFlashing(SEL)).toBe(true);
    });

    test('returns true when cell contains .ag-value-change-value-highlight child', () => {
      document.body.innerHTML = `
        <div class="ag-center-cols-container">
          <div row-index="0">
            <div col-id="price">
              <span class="ag-value-change-value-highlight">150</span>
            </div>
          </div>
        </div>`;
      expect(probes.isCellFlashing(SEL)).toBe(true);
    });

    test('returns false when no flash indicators are present', () => {
      buildCell('100');
      expect(probes.isCellFlashing(SEL)).toBe(false);
    });

    test('returns false when selector matches no element', () => {
      document.body.innerHTML = '';
      expect(probes.isCellFlashing(SEL)).toBe(false);
    });
  });

  describe('isCellStable', () => {
    test('returns true when cell has no flash class', () => {
      buildCell('100');
      expect(probes.isCellStable(SEL)).toBe(true);
    });

    test('returns false when cell has ag-cell-data-changed class', () => {
      buildCell('100', 'ag-cell-data-changed');
      expect(probes.isCellStable(SEL)).toBe(false);
    });

    test('returns false when selector matches no element', () => {
      document.body.innerHTML = '';
      expect(probes.isCellStable(SEL)).toBe(false);
    });
  });

  describe('hasCellValueChanged', () => {
    test('returns true when current text differs from initial value', () => {
      buildCell('200');
      expect(probes.hasCellValueChanged(SEL, '100')).toBe(true);
    });

    test('returns false when current text equals initial value', () => {
      buildCell('100');
      expect(probes.hasCellValueChanged(SEL, '100')).toBe(false);
    });

    test('returns false for an empty cell (handles loading state)', () => {
      buildCell('');
      expect(probes.hasCellValueChanged(SEL, 'anything')).toBe(false);
    });

    test('returns false when selector matches no element', () => {
      document.body.innerHTML = '';
      expect(probes.hasCellValueChanged(SEL, 'x')).toBe(false);
    });

    test('trims whitespace when comparing values', () => {
      buildCell('  42.5  ');
      expect(probes.hasCellValueChanged(SEL, '42.5')).toBe(false);
      expect(probes.hasCellValueChanged(SEL, '99')).toBe(true);
    });
  });

  describe('getCellText', () => {
    test('returns trimmed text content', () => {
      buildCell('  3.14  ');
      expect(probes.getCellText(SEL)).toBe('3.14');
    });

    test('returns null when selector matches no element', () => {
      document.body.innerHTML = '';
      expect(probes.getCellText(SEL)).toBeNull();
    });
  });
});
