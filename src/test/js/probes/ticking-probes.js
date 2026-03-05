/**
 * Live ticking cell probes — detect AG Grid cell flash animations and value changes.
 *
 * AG Grid Finance Demo uses two flash mechanisms:
 *   1. ag-cell-data-changed CSS class   (enableCellChangeFlash=true)
 *   2. .ag-value-change-value-highlight child element  (agAnimateShowChangeCellRenderer)
 *
 * All functions accept a CSS selector that must resolve to a single cell element.
 * They return simple boolean / string values so they can be used directly as
 * page.waitForFunction() predicates in Java:
 *
 *   page.waitForFunction("args => window.agGridProbes.ticking.isCellFlashing(args[0])",
 *                        List.of(selector), options);
 *
 * @param {Document} doc
 * @returns {object}
 */
function createTickingProbes(doc) {

  return {
    /**
     * Returns true when the cell is currently flashing (live update in progress).
     */
    isCellFlashing: function (selector) {
      var el = doc.querySelector(selector);
      if (!el) return false;
      return el.classList.contains('ag-cell-data-changed') ||
             !!el.querySelector('.ag-value-change-value-highlight');
    },

    /**
     * Returns true when the flash animation has finished and the cell is stable.
     * The opposite of isCellFlashing — call after isCellFlashing to confirm
     * the update cycle is complete before reading the value.
     */
    isCellStable: function (selector) {
      var el = doc.querySelector(selector);
      return el ? !el.classList.contains('ag-cell-data-changed') : false;
    },

    /**
     * Returns true when the cell text has changed from initialValue.
     * Returns false for empty cells (handles transient loading state).
     * Suitable for use as a waitForFunction predicate to detect live ticks.
     */
    hasCellValueChanged: function (selector, initialValue) {
      var el = doc.querySelector(selector);
      if (!el) return false;
      var text = el.textContent.trim();
      return text !== '' && text !== initialValue;
    },

    /**
     * Returns the current trimmed text content of the cell, or null if not in DOM.
     */
    getCellText: function (selector) {
      var el = doc.querySelector(selector);
      return el ? el.textContent.trim() : null;
    }
  };
}

if (typeof module !== 'undefined') {
  module.exports = { createTickingProbes: createTickingProbes };
}
