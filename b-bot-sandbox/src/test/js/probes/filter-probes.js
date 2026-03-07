/**
 * AG Grid filter probes — apply and clear column filter models via the Grid API.
 *
 * AG Grid v33 uses different filter model formats per column type.
 * The Java caller tries applyTextFilter first; if AG Grid throws an unhandled
 * Promise rejection (surfaced by Playwright as a PlaywrightException), the Java
 * caller retries with applySetFilter.
 *
 *   Text filter: { filterType: 'text', type: 'contains', filter: value }
 *   Set filter:  { filterType: 'set', values: [value] }
 *
 * @param {object} discovery  Result of createApiDiscovery(doc, win)
 * @returns {object}
 */
function createFilterProbes(discovery) {

  function getApi() {
    return discovery.findGridApi('setFilterModel');
  }

  return {
    /**
     * Applies a text-contains filter to the given column.
     * Has no effect when the API is unavailable.
     */
    applyTextFilter: function (colId, value) {
      var api = getApi();
      if (!api) return;
      var m = {};
      m[colId] = { filterType: 'text', type: 'contains', filter: value };
      api.setFilterModel(m);
    },

    /**
     * Applies a set filter (discrete-value column, e.g. 'instrument').
     * Has no effect when the API is unavailable.
     */
    applySetFilter: function (colId, value) {
      var api = getApi();
      if (!api) return;
      var m = {};
      m[colId] = { filterType: 'set', values: [value] };
      api.setFilterModel(m);
    },

    /**
     * Clears all active column filters (passes null model to AG Grid).
     */
    clearAllFilters: function () {
      var api = getApi();
      if (api) api.setFilterModel(null);
    },

    /**
     * Returns true when the API is accessible for filter operations.
     */
    isAvailable: function () {
      return getApi() !== null;
    }
  };
}

if (typeof module !== 'undefined') {
  module.exports = { createFilterProbes: createFilterProbes };
}
