const { createApiDiscovery } = require('../probes/api-discovery');

describe('createApiDiscovery', () => {

  afterEach(() => {
    document.body.innerHTML = '';
  });

  test('returns null when .ag-root-wrapper is not in the DOM', () => {
    document.body.innerHTML = '<div></div>';
    const discovery = createApiDiscovery(document, {});
    expect(discovery.findGridApi('forEachNode')).toBeNull();
  });

  test('returns null when capability is absent from the found API', () => {
    document.body.innerHTML = '<div class="ag-root-wrapper"></div>';
    const win = { gridApi: { someOtherMethod: jest.fn() } };
    const discovery = createApiDiscovery(document, win);
    expect(discovery.findGridApi('forEachNode')).toBeNull();
  });

  test('finds API via window.gridApi global', () => {
    document.body.innerHTML = '<div class="ag-root-wrapper"></div>';
    const mockApi = { forEachNode: jest.fn() };
    const win = { gridApi: mockApi };
    const discovery = createApiDiscovery(document, win);
    expect(discovery.findGridApi('forEachNode')).toBe(mockApi);
  });

  test('finds API via window.agGridApi global', () => {
    document.body.innerHTML = '<div class="ag-root-wrapper"></div>';
    const mockApi = { setFilterModel: jest.fn() };
    const win = { agGridApi: mockApi };
    const discovery = createApiDiscovery(document, win);
    expect(discovery.findGridApi('setFilterModel')).toBe(mockApi);
  });

  test('finds API via memoizedProps on React fibre node', () => {
    document.body.innerHTML = '<div class="ag-root-wrapper"></div>';
    const mockApi = { setFilterModel: jest.fn() };
    const el = document.querySelector('.ag-root-wrapper');
    el['__reactFiber$abc123'] = {
      memoizedProps: { api: mockApi },
      stateNode: null,
      memoizedState: null,
      return: null
    };
    const discovery = createApiDiscovery(document, {});
    expect(discovery.findGridApi('setFilterModel')).toBe(mockApi);
  });

  test('finds API via stateNode on React fibre node', () => {
    document.body.innerHTML = '<div class="ag-root-wrapper"></div>';
    const mockApi = { forEachNode: jest.fn() };
    const el = document.querySelector('.ag-root-wrapper');
    el['__reactInternals$xyz'] = {
      memoizedProps: {},
      stateNode: { api: mockApi },
      memoizedState: null,
      return: null
    };
    const discovery = createApiDiscovery(document, {});
    expect(discovery.findGridApi('forEachNode')).toBe(mockApi);
  });

  test('walks up the fibre return chain to find the API', () => {
    document.body.innerHTML = '<div class="ag-root-wrapper"></div>';
    const mockApi = { forEachNode: jest.fn() };
    const el = document.querySelector('.ag-root-wrapper');
    const parentFibre = {
      memoizedProps: { api: mockApi },
      stateNode: null,
      memoizedState: null,
      return: null
    };
    el['__reactFiber$root'] = {
      memoizedProps: {},
      stateNode: null,
      memoizedState: null,
      return: parentFibre
    };
    const discovery = createApiDiscovery(document, {});
    expect(discovery.findGridApi('forEachNode')).toBe(mockApi);
  });

  test('finds API via __agComponent legacy path', () => {
    document.body.innerHTML = '<div class="ag-root-wrapper"></div>';
    const mockApi = { forEachNode: jest.fn() };
    const el = document.querySelector('.ag-root-wrapper');
    el.__agComponent = { gridOptions: { api: mockApi } };
    const discovery = createApiDiscovery(document, {});
    expect(discovery.findGridApi('forEachNode')).toBe(mockApi);
  });
});
