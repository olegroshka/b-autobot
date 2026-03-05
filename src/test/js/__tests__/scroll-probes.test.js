const { createScrollProbes } = require('../probes/scroll-probes');

function buildViewport({ scrollTop = 0, clientHeight = 100, scrollHeight = 1000 } = {}) {
  document.body.innerHTML = '<div class="ag-body-viewport"></div>';
  const vp = document.querySelector('.ag-body-viewport');
  Object.defineProperty(vp, 'scrollTop',    { value: scrollTop,    writable: true });
  Object.defineProperty(vp, 'clientHeight', { value: clientHeight, writable: true });
  Object.defineProperty(vp, 'scrollHeight', { value: scrollHeight, writable: true });
  return vp;
}

describe('createScrollProbes', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  describe('scrollToTop', () => {
    test('sets scrollTop to 0', () => {
      const vp = buildViewport({ scrollTop: 500 });
      const sp = createScrollProbes(document);
      sp.scrollToTop();
      expect(vp.scrollTop).toBe(0);
    });

    test('is a no-op when viewport element does not exist', () => {
      document.body.innerHTML = '';
      const sp = createScrollProbes(document);
      expect(() => sp.scrollToTop()).not.toThrow();
    });
  });

  describe('scrollDown', () => {
    test('increases scrollTop by 90% of clientHeight', () => {
      const vp = buildViewport({ scrollTop: 0, clientHeight: 200 });
      const sp = createScrollProbes(document);
      sp.scrollDown();
      expect(vp.scrollTop).toBe(180);
    });

    test('is a no-op when viewport element does not exist', () => {
      document.body.innerHTML = '';
      const sp = createScrollProbes(document);
      expect(() => sp.scrollDown()).not.toThrow();
    });
  });

  describe('isAtBottom', () => {
    test('returns true when scrollTop + clientHeight >= scrollHeight - 10', () => {
      buildViewport({ scrollTop: 900, clientHeight: 100, scrollHeight: 1000 });
      const sp = createScrollProbes(document);
      expect(sp.isAtBottom()).toBe(true);
    });

    test('returns false when there is more content to scroll', () => {
      buildViewport({ scrollTop: 0, clientHeight: 100, scrollHeight: 1000 });
      const sp = createScrollProbes(document);
      expect(sp.isAtBottom()).toBe(false);
    });

    test('returns true within the 10px tolerance', () => {
      buildViewport({ scrollTop: 895, clientHeight: 100, scrollHeight: 1000 });
      const sp = createScrollProbes(document);
      expect(sp.isAtBottom()).toBe(true);
    });

    test('returns true when no viewport element exists', () => {
      document.body.innerHTML = '';
      const sp = createScrollProbes(document);
      expect(sp.isAtBottom()).toBe(true);
    });
  });
});
