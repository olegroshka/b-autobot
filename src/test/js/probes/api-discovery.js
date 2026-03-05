/**
 * AG Grid API discovery probe.
 *
 * Locates the AG Grid JavaScript API via three strategies (in order):
 *   1. window.gridApi / window.agGridApi  — set by some AG Grid demos
 *   2. React fibre traversal from .ag-root-wrapper  — AG Grid React (v28+)
 *   3. __agComponent.gridOptions.api  — older AG Grid Community / Enterprise
 *
 * Factory pattern: pass (doc, win) explicitly so Jest tests can inject jsdom
 * mocks without relying on the global document/window.
 *
 * @param {Document} doc  The document to query
 * @param {Window}   win  The window to check for globals
 * @returns {{ findGridApi: function(string): (object|null) }}
 */
function createApiDiscovery(doc, win) {

  /**
   * Returns the first AG Grid API object that exposes the given capability method,
   * or null if no API can be located.
   *
   * @param {string} capability  Method name to probe, e.g. 'setFilterModel', 'forEachNode'
   * @returns {object|null}
   */
  function findGridApi(capability) {
    /* Strategy 1: global shortcuts set by some AG Grid demos */
    var api = (win && (win.gridApi || win.agGridApi)) || null;
    if (api && typeof api[capability] === 'function') return api;

    var el = doc.querySelector('.ag-root-wrapper');
    if (!el) return null;

    /* Strategy 2: React fibre traversal */
    var fk = Object.keys(el).find(function (k) {
      return k.startsWith('__reactFiber') || k.startsWith('__reactInternals');
    });
    if (fk) {
      var f = el[fk];
      var n = 0;
      while (f && n++ < 2000) {
        if (f.memoizedProps && f.memoizedProps.api &&
            typeof f.memoizedProps.api[capability] === 'function') {
          return f.memoizedProps.api;
        }
        var s = f.stateNode;
        if (s && typeof s === 'object' && !s.nodeType && s.api &&
            typeof s.api[capability] === 'function') {
          return s.api;
        }
        var st = f.memoizedState;
        while (st) {
          if (st.memoizedState && st.memoizedState.current &&
              typeof st.memoizedState.current[capability] === 'function') {
            return st.memoizedState.current;
          }
          st = st.next;
        }
        f = f.return;
      }
    }

    /* Strategy 3: legacy __agComponent path (AG Grid Community older versions) */
    if (el.__agComponent && el.__agComponent.gridOptions &&
        el.__agComponent.gridOptions.api &&
        typeof el.__agComponent.gridOptions.api[capability] === 'function') {
      return el.__agComponent.gridOptions.api;
    }

    return null;
  }

  return { findGridApi: findGridApi };
}

if (typeof module !== 'undefined') {
  module.exports = { createApiDiscovery: createApiDiscovery };
}
