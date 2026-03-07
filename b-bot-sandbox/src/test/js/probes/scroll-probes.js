/**
 * AG Grid viewport scroll probes.
 *
 * All operations target the .ag-body-viewport scrollable container.
 * Functions are no-ops when the viewport element is not in the DOM.
 *
 * @param {Document} doc
 * @returns {object}
 */
function createScrollProbes(doc) {
  var VIEWPORT_SEL = '.ag-body-viewport';

  function getViewport() {
    return doc.querySelector(VIEWPORT_SEL);
  }

  return {
    /**
     * Resets the grid viewport scroll position to the top.
     */
    scrollToTop: function () {
      var vp = getViewport();
      if (vp) vp.scrollTop = 0;
    },

    /**
     * Scrolls the viewport down by 90% of its visible client height.
     * Used by the scroll-probe fallback in GridHarness to page through rows.
     */
    scrollDown: function () {
      var vp = getViewport();
      if (vp) vp.scrollTop += vp.clientHeight * 0.9;
    },

    /**
     * Returns true when the viewport is at (or within 10 px of) the bottom.
     * Returns true when no viewport element exists (treated as trivially at bottom).
     */
    isAtBottom: function () {
      var vp = getViewport();
      if (!vp) return true;
      return vp.scrollTop + vp.clientHeight >= vp.scrollHeight - 10;
    }
  };
}

if (typeof module !== 'undefined') {
  module.exports = { createScrollProbes: createScrollProbes };
}
