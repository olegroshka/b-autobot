package com.bbot.core;

import com.microsoft.playwright.Page;

import java.time.Duration;

/**
 * Abstraction for asserting on AG Grid "ticking" cells whose values update
 * at high frequency.
 *
 * <p>The default implementation is {@link TickingCellHelper}, which uses
 * Playwright's {@code waitForFunction} and the injected {@code agGridProbes}
 * bundle. Consumers may provide mock implementations for unit testing.
 *
 * @see TickingCellHelper
 */
public interface CellAssertions {

    /**
     * Blocks until the cell at {@code colId / rowIndex} shows a different value,
     * then returns the new text.
     */
    String waitForCellUpdate(Page page, String colId, int rowIndex, Duration timeout);

    /**
     * Asserts that the cell text is numeric and within [{@code min}, {@code max}].
     */
    void assertCellValueInRange(Page page, String colId, int rowIndex,
                                 double min, double max, Duration timeout);

    /**
     * Waits until AG Grid's tick-flash class is applied to the target cell.
     */
    void waitForCellFlash(Page page, String colId, int rowIndex, Duration timeout);

    /**
     * Waits until the flash animation is gone and the cell value is stable.
     */
    void waitForCellStable(Page page, String colId, int rowIndex, Duration timeout);

    /**
     * Waits for a tick, waits for the animation to finish, then returns the
     * stable text content.
     */
    String readStableValueAfterTick(Page page, String colId, int rowIndex,
                                     Duration flashTimeout, Duration stableTimeout);
}

