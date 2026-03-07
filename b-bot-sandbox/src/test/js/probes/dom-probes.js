/**
 * Pure DOM probes — no AG Grid API required.
 *
 * All functions query the live DOM to answer questions about what is currently
 * visible in the virtualised AG Grid viewport.  Because they are pure DOM
 * queries they work even when the Grid JS API is unavailable (e.g. mocked DOM
 * in unit tests or in the scroll-probe fallback phase).
 *
 * AG Grid DOM conventions assumed:
 *   Row container : .ag-center-cols-container
 *   Row element   : [row-index='N']
 *   Cell element  : [col-id='colName']
 *   Viewport      : .ag-body-viewport
 *
 * @param {Document} doc
 * @returns {object}
 */
function createDomProbes(doc) {
  var ROWS_SEL    = '.ag-center-cols-container';
  var VIEWPORT_SEL = '.ag-body-viewport';

  return {
    /**
     * Returns the trimmed text of every currently-visible cell in colId.
     * Due to virtualisation this reflects only rows in the visible viewport.
     */
    getVisibleCellTexts: function (colId) {
      var cells = doc.querySelectorAll(ROWS_SEL + ' [col-id="' + colId + '"]');
      return Array.from(cells).map(function (el) { return el.textContent.trim(); });
    },

    /**
     * Returns the trimmed text of the cell matched by CSS selector, or null.
     */
    getCellText: function (selector) {
      var el = doc.querySelector(selector);
      return el ? el.textContent.trim() : null;
    },

    /**
     * Finds the DOM row-index of the first visible cell in colId whose trimmed
     * text equals value exactly.  Returns -1 when not found.
     */
    findRowIndexByText: function (colId, value) {
      var cells = Array.from(
        doc.querySelectorAll(ROWS_SEL + ' [col-id="' + colId + '"]'));
      var match = cells.find(function (el) {
        return el.textContent.trim() === value;
      });
      if (!match) return -1;
      var row = match.closest('[row-index]');
      return row ? parseInt(row.getAttribute('row-index'), 10) : -1;
    },

    /**
     * Returns true when every visible cell in colId contains text (case-insensitive).
     * Used to poll after applying a column filter.
     */
    areAllVisibleCellsContaining: function (colId, text) {
      var cells = doc.querySelectorAll(ROWS_SEL + ' [col-id="' + colId + '"]');
      if (!cells.length) return false;
      var lc = text.toLowerCase();
      return Array.from(cells).every(function (c) {
        return c.textContent.toLowerCase().includes(lc);
      });
    },

    /**
     * Returns true when at least one row node is rendered in the container.
     */
    hasRowsInViewport: function () {
      return doc.querySelectorAll(ROWS_SEL + ' [row-index]').length > 0;
    },

    /**
     * Returns true when the row with the given row-index is present in the DOM.
     * Used to confirm AG Grid has rendered a row after ensureIndexVisible().
     */
    isRowInDom: function (rowIndex) {
      return !!doc.querySelector(ROWS_SEL + ' [row-index="' + rowIndex + '"]');
    },

    /**
     * Returns the highest row-index integer currently present in the DOM, or -1.
     */
    getLastDomRowIndex: function () {
      var rows = doc.querySelectorAll(ROWS_SEL + ' [row-index]');
      if (!rows.length) return -1;
      return Math.max.apply(null, Array.from(rows).map(function (el) {
        return parseInt(el.getAttribute('row-index'), 10);
      }));
    },

    /**
     * Returns true when the grid viewport cannot scroll further down
     * (i.e. scrollTop + clientHeight >= scrollHeight - 10px tolerance).
     */
    isViewportAtBottom: function () {
      var vp = doc.querySelector(VIEWPORT_SEL);
      if (!vp) return true;
      return vp.scrollTop + vp.clientHeight >= vp.scrollHeight - 10;
    },

    /**
     * Returns true when the cell at selector is currently flashing.
     * Supports two AG Grid flash mechanisms:
     *   • ag-cell-data-changed class  (enableCellChangeFlash=true)
     *   • .ag-value-change-value-highlight child  (agAnimateShowChangeCellRenderer)
     */
    isCellFlashing: function (selector) {
      var el = doc.querySelector(selector);
      if (!el) return false;
      return el.classList.contains('ag-cell-data-changed') ||
             !!el.querySelector('.ag-value-change-value-highlight');
    },

    /**
     * Returns true when the flash animation has finished and the cell is stable.
     */
    isCellStable: function (selector) {
      var el = doc.querySelector(selector);
      return el ? !el.classList.contains('ag-cell-data-changed') : false;
    },

    /**
     * Returns true when the cell text has changed from initialValue.
     * Suitable for use as a waitForFunction predicate to detect live ticks.
     */
    hasCellValueChanged: function (selector, initialValue) {
      var el = doc.querySelector(selector);
      if (!el) return false;
      var text = el.textContent.trim();
      return text !== '' && text !== initialValue;
    }
  };
}

if (typeof module !== 'undefined') {
  module.exports = { createDomProbes: createDomProbes };
}
