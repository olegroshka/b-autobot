/**
 * AG Grid Probes Bundle
 *
 * Browser-injectable IIFE that registers window.agGridProbes with five probe
 * namespaces.  Inject via Playwright's page.addInitScript() so the probes are
 * available before any page script runs.
 *
 * Namespace layout:
 *   window.agGridProbes.dom      — pure DOM queries (no AG Grid API)
 *   window.agGridProbes.gridApi  — AG Grid API operations (forEachNode, ensureIndexVisible)
 *   window.agGridProbes.filter   — filter model apply / clear
 *   window.agGridProbes.scroll   — viewport scroll operations
 *   window.agGridProbes.ticking  — live ticking cell detection
 *
 * Source modules live in sibling files (api-discovery.js, dom-probes.js, …)
 * and are tested independently with Jest + jsdom.
 * Keep this bundle in sync when modifying individual probe files.
 */
(function (win, doc) {
  'use strict';

  /* ── api-discovery ────────────────────────────────────────────────────────── */

  function findGridApi(capability) {
    var api = (win.gridApi || win.agGridApi) || null;
    if (api && typeof api[capability] === 'function') return api;

    var el = doc.querySelector('.ag-root-wrapper');
    if (!el) return null;

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

    if (el.__agComponent && el.__agComponent.gridOptions &&
        el.__agComponent.gridOptions.api &&
        typeof el.__agComponent.gridOptions.api[capability] === 'function') {
      return el.__agComponent.gridOptions.api;
    }

    return null;
  }

  /* ── dom-probes ───────────────────────────────────────────────────────────── */

  var ROWS     = '.ag-center-cols-container';
  var VIEWPORT = '.ag-body-viewport';

  var dom = {
    getVisibleCellTexts: function (colId) {
      var cells = doc.querySelectorAll(ROWS + ' [col-id="' + colId + '"]');
      return Array.from(cells).map(function (el) { return el.textContent.trim(); });
    },

    getCellText: function (selector) {
      var el = doc.querySelector(selector);
      return el ? el.textContent.trim() : null;
    },

    findRowIndexByText: function (colId, value) {
      var cells = Array.from(
        doc.querySelectorAll(ROWS + ' [col-id="' + colId + '"]'));
      var match = cells.find(function (el) {
        return el.textContent.trim() === value;
      });
      if (!match) return -1;
      var row = match.closest('[row-index]');
      return row ? parseInt(row.getAttribute('row-index'), 10) : -1;
    },

    areAllVisibleCellsContaining: function (colId, text) {
      var cells = doc.querySelectorAll(ROWS + ' [col-id="' + colId + '"]');
      if (!cells.length) return false;
      var lc = text.toLowerCase();
      return Array.from(cells).every(function (c) {
        return c.textContent.toLowerCase().includes(lc);
      });
    },

    hasRowsInViewport: function () {
      return doc.querySelectorAll(ROWS + ' [row-index]').length > 0;
    },

    isRowInDom: function (rowIndex) {
      return !!doc.querySelector(ROWS + ' [row-index="' + rowIndex + '"]');
    },

    getLastDomRowIndex: function () {
      var rows = doc.querySelectorAll(ROWS + ' [row-index]');
      if (!rows.length) return -1;
      return Math.max.apply(null, Array.from(rows).map(function (el) {
        return parseInt(el.getAttribute('row-index'), 10);
      }));
    },

    isViewportAtBottom: function () {
      var vp = doc.querySelector(VIEWPORT);
      if (!vp) return true;
      return vp.scrollTop + vp.clientHeight >= vp.scrollHeight - 10;
    },

    isCellFlashing: function (selector) {
      var el = doc.querySelector(selector);
      if (!el) return false;
      return el.classList.contains('ag-cell-data-changed') ||
             !!el.querySelector('.ag-value-change-value-highlight');
    },

    isCellStable: function (selector) {
      var el = doc.querySelector(selector);
      return el ? !el.classList.contains('ag-cell-data-changed') : false;
    },

    hasCellValueChanged: function (selector, initialValue) {
      var el = doc.querySelector(selector);
      if (!el) return false;
      var text = el.textContent.trim();
      return text !== '' && text !== initialValue;
    }
  };

  /* ── grid-api-probes ──────────────────────────────────────────────────────── */

  var gridApi = {
    findRowIndexByDataValue: function (colId, value) {
      var api = findGridApi('forEachNode');
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

    ensureRowVisible: function (rowIndex) {
      var api = findGridApi('ensureIndexVisible');
      if (!api) return false;
      api.ensureIndexVisible(rowIndex, 'middle');
      return true;
    },

    isApiAvailable: function () {
      return findGridApi('forEachNode') !== null;
    }
  };

  /* ── filter-probes ────────────────────────────────────────────────────────── */

  var filter = {
    applyTextFilter: function (colId, value) {
      var api = findGridApi('setFilterModel');
      if (!api) return;
      var m = {};
      m[colId] = { filterType: 'text', type: 'contains', filter: value };
      api.setFilterModel(m);
    },

    applySetFilter: function (colId, value) {
      var api = findGridApi('setFilterModel');
      if (!api) return;
      var m = {};
      m[colId] = { filterType: 'set', values: [value] };
      api.setFilterModel(m);
    },

    clearAllFilters: function () {
      var api = findGridApi('setFilterModel');
      if (api) api.setFilterModel(null);
    }
  };

  /* ── scroll-probes ────────────────────────────────────────────────────────── */

  var scroll = {
    scrollToTop: function () {
      var vp = doc.querySelector(VIEWPORT);
      if (vp) vp.scrollTop = 0;
    },

    scrollDown: function () {
      var vp = doc.querySelector(VIEWPORT);
      if (vp) vp.scrollTop += vp.clientHeight * 0.9;
    },

    isAtBottom: function () {
      var vp = doc.querySelector(VIEWPORT);
      if (!vp) return true;
      return vp.scrollTop + vp.clientHeight >= vp.scrollHeight - 10;
    }
  };

  /* ── ticking-probes ───────────────────────────────────────────────────────── */

  var ticking = {
    isCellFlashing: function (selector) {
      var el = doc.querySelector(selector);
      if (!el) return false;
      return el.classList.contains('ag-cell-data-changed') ||
             !!el.querySelector('.ag-value-change-value-highlight');
    },

    isCellStable: function (selector) {
      var el = doc.querySelector(selector);
      return el ? !el.classList.contains('ag-cell-data-changed') : false;
    },

    hasCellValueChanged: function (selector, initialValue) {
      var el = doc.querySelector(selector);
      if (!el) return false;
      var text = el.textContent.trim();
      return text !== '' && text !== initialValue;
    },

    getCellText: function (selector) {
      var el = doc.querySelector(selector);
      return el ? el.textContent.trim() : null;
    }
  };

  /* ── register namespace ───────────────────────────────────────────────────── */

  win.agGridProbes = {
    dom:     dom,
    gridApi: gridApi,
    filter:  filter,
    scroll:  scroll,
    ticking: ticking
  };

}(window, document));
