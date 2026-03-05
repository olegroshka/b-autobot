const { createApiDiscovery } = require('../probes/api-discovery');
const { createFilterProbes } = require('../probes/filter-probes');

function attachMockApi(mockApi) {
  document.body.innerHTML = '<div class="ag-root-wrapper"></div>';
  const el = document.querySelector('.ag-root-wrapper');
  el['__reactFiber$test'] = {
    memoizedProps: { api: mockApi },
    stateNode: null,
    memoizedState: null,
    return: null
  };
}

describe('createFilterProbes', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  describe('applyTextFilter', () => {
    test('calls setFilterModel with a text-contains model', () => {
      const mockApi = { setFilterModel: jest.fn() };
      attachMockApi(mockApi);
      const fp = createFilterProbes(createApiDiscovery(document, {}));
      fp.applyTextFilter('instrument', 'Stock');
      expect(mockApi.setFilterModel).toHaveBeenCalledWith({
        instrument: { filterType: 'text', type: 'contains', filter: 'Stock' }
      });
    });

    test('is a no-op when no API is available', () => {
      document.body.innerHTML = '<div></div>';
      const fp = createFilterProbes(createApiDiscovery(document, {}));
      expect(() => fp.applyTextFilter('col', 'val')).not.toThrow();
    });
  });

  describe('applySetFilter', () => {
    test('calls setFilterModel with a set-filter model', () => {
      const mockApi = { setFilterModel: jest.fn() };
      attachMockApi(mockApi);
      const fp = createFilterProbes(createApiDiscovery(document, {}));
      fp.applySetFilter('instrument', 'ETF');
      expect(mockApi.setFilterModel).toHaveBeenCalledWith({
        instrument: { filterType: 'set', values: ['ETF'] }
      });
    });

    test('is a no-op when no API is available', () => {
      document.body.innerHTML = '<div></div>';
      const fp = createFilterProbes(createApiDiscovery(document, {}));
      expect(() => fp.applySetFilter('col', 'val')).not.toThrow();
    });
  });

  describe('clearAllFilters', () => {
    test('calls setFilterModel(null)', () => {
      const mockApi = { setFilterModel: jest.fn() };
      attachMockApi(mockApi);
      const fp = createFilterProbes(createApiDiscovery(document, {}));
      fp.clearAllFilters();
      expect(mockApi.setFilterModel).toHaveBeenCalledWith(null);
    });

    test('is a no-op when no API is available', () => {
      document.body.innerHTML = '<div></div>';
      const fp = createFilterProbes(createApiDiscovery(document, {}));
      expect(() => fp.clearAllFilters()).not.toThrow();
    });
  });

  describe('isAvailable', () => {
    test('returns true when setFilterModel API is found', () => {
      const mockApi = { setFilterModel: jest.fn() };
      attachMockApi(mockApi);
      const fp = createFilterProbes(createApiDiscovery(document, {}));
      expect(fp.isAvailable()).toBe(true);
    });

    test('returns false when no API is found', () => {
      document.body.innerHTML = '<div></div>';
      const fp = createFilterProbes(createApiDiscovery(document, {}));
      expect(fp.isAvailable()).toBe(false);
    });
  });
});
