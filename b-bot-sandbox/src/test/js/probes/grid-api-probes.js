/**
 * AG Grid API probes — operations that require the AG Grid JavaScript API.
 *
 * Falls back gracefully (returns null / false) when the API cannot be located
 * so callers can detect unavailability and fall through to Phase 3 scroll-probe.
 *
 * @param {object} discovery  Result of createApiDiscovery(doc, win)
 * @returns {object}
 */
function createGridApiProbes(discovery) {

  return {
    /**
     * Searches the AG Grid data model (not the DOM) for the first row where
     * data[colId] matches value exactly (after trimming both sides).
     *
     * Returns the integer rowIndex, or null when not found or no API.
     */
    findRowIndexByDataValue: function (colId, value) {
      var api = discovery.findGridApi('forEachNode');
      if (!api) return null;
      var found = null;
      api.forEachNode(function (node) {
        if (found !== null) return;
        if (node.data &&
            String(node.data[colId]).trim() === String(value).trim()) {
          found = node.rowIndex;
        }
      });
      return found;
    },

    /**
     * Calls api.ensureIndexVisible(rowIndex, 'middle') to scroll the grid and
     * trigger DOM rendering for the target row.
     *
     * Returns true when the API was available and the call was made, false otherwise.
     */
    ensureRowVisible: function (rowIndex) {
      var api = discovery.findGridApi('ensureIndexVisible');
      if (!api) return false;
      api.ensureIndexVisible(rowIndex, 'middle');
      return true;
    },

    /**
     * Returns true when the AG Grid API (forEachNode) is currently accessible.
     * Used by callers to decide whether Phase 2 is possible.
     */
    isApiAvailable: function () {
      return discovery.findGridApi('forEachNode') !== null;
    }
  };
}

if (typeof module !== 'undefined') {
  module.exports = { createGridApiProbes: createGridApiProbes };
}
