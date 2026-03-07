const { createApiDiscovery } = require('../probes/api-discovery');
const { createGridApiProbes } = require('../probes/grid-api-probes');

function makeMockApi(rows) {
  return {
    forEachNode: jest.fn(cb => rows.forEach(cb)),
    ensureIndexVisible: jest.fn()
  };
}

function attachApiToDom(mockApi) {
  document.body.innerHTML = '<div class="ag-root-wrapper"></div>';
  const el = document.querySelector('.ag-root-wrapper');
  el['__reactFiber$test'] = {
    memoizedProps: { api: mockApi },
    stateNode: null,
    memoizedState: null,
    return: null
  };
}

describe('createGridApiProbes', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  describe('findRowIndexByDataValue', () => {
    test('returns the correct row-index when the value is found', () => {
      const mockApi = makeMockApi([
        { data: { ticker: 'AAPL' }, rowIndex: 0 },
        { data: { ticker: 'MSFT' }, rowIndex: 1 },
        { data: { ticker: 'GOOG' }, rowIndex: 2 }
      ]);
      attachApiToDom(mockApi);
      const gp = createGridApiProbes(createApiDiscovery(document, {}));
      expect(gp.findRowIndexByDataValue('ticker', 'MSFT')).toBe(1);
    });

    test('returns null when the value is not in the data model', () => {
      const mockApi = makeMockApi([
        { data: { ticker: 'AAPL' }, rowIndex: 0 }
      ]);
      attachApiTodom(mockApi);
      const gp = createGridApiProbes(createApiDiscovery(document, {}));
      expect(gp.findRowIndexByDataValue('ticker', 'TSLA')).toBeNull();
    });

    test('returns null when no API is available', () => {
      document.body.innerHTML = '<div></div>';
      const gp = createGridApiProbes(createApiDiscovery(document, {}));
      expect(gp.findRowIndexByDataValue('ticker', 'AAPL')).toBeNull();
    });

    test('trims whitespace when matching values', () => {
      const mockApi = makeMockApi([
        { data: { ticker: ' AAPL ' }, rowIndex: 5 }
      ]);
      attachApiTodom(mockApi);
      const gp = createGridApiProbes(createApiDiscovery(document, {}));
      expect(gp.findRowIndexByDataValue('ticker', 'AAPL')).toBe(5);
    });

    test('stops searching after the first match', () => {
      const mockApi = makeMockApi([
        { data: { ticker: 'AAPL' }, rowIndex: 0 },
        { data: { ticker: 'AAPL' }, rowIndex: 3 }
      ]);
      attachApiTodom(mockApi);
      const gp = createGridApiProbes(createApiDiscovery(document, {}));
      expect(gp.findRowIndexByDataValue('ticker', 'AAPL')).toBe(0);
    });
  });

  describe('ensureRowVisible', () => {
    test('calls ensureIndexVisible with middle alignment and returns true', () => {
      const mockApi = makeMockApi([]);
      mockApi.ensureIndexVisible = jest.fn();
      attachApiTodom(mockApi);
      const gp = createGridApiProbes(createApiDiscovery(document, {}));
      expect(gp.ensureRowVisible(7)).toBe(true);
      expect(mockApi.ensureIndexVisible).toHaveBeenCalledWith(7, 'middle');
    });

    test('returns false when no API is available', () => {
      document.body.innerHTML = '<div></div>';
      const gp = createGridApiProbes(createApiDiscovery(document, {}));
      expect(gp.ensureRowVisible(0)).toBe(false);
    });
  });

  describe('isApiAvailable', () => {
    test('returns true when API with forEachNode is found', () => {
      const mockApi = makeMockApi([]);
      attachApiTodom(mockApi);
      const gp = createGridApiProbes(createApiDiscovery(document, {}));
      expect(gp.isApiAvailable()).toBe(true);
    });

    test('returns false when no API is found', () => {
      document.body.innerHTML = '<div></div>';
      const gp = createGridApiProbes(createApiDiscovery(document, {}));
      expect(gp.isApiAvailable()).toBe(false);
    });
  });
});

// Helper used in tests above (typo-safe alias)
function attachApiTodom(mockApi) {
  attachApiToDom(mockApi);
}
