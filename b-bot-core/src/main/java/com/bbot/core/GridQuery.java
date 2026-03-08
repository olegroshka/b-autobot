package com.bbot.core;

import com.microsoft.playwright.Locator;

import java.time.Duration;

/**
 * Abstraction for querying AG Grid rows in a virtualised grid.
 *
 * <p>The default implementation is {@link GridHarness}, which uses Playwright
 * and the injected {@code agGridProbes} bundle. Consumers may provide mock
 * implementations for unit testing step definitions without a browser.
 *
 * @see GridHarness
 */
public interface GridQuery {

    /**
     * Finds the first AG Grid cell where {@code col-id} equals {@code colId}
     * and whose trimmed text equals {@code cellText} exactly.
     * Handles virtualisation transparently.
     *
     * @return A {@link Locator} pointing to the matched cell
     */
    Locator findRowByCellValue(String colId, String cellText, Duration timeout);

    /**
     * Returns the text content of the cell at {@code colId} in the same row as
     * {@code anchorCell}.
     */
    String getSiblingCellText(Locator anchorCell, String colId);

    /**
     * Returns the integer {@code row-index} attribute of the row containing
     * the given cell locator.
     */
    int getRowIndex(Locator cellLocator);

    /**
     * Returns the trimmed text content of the cell at the given row-index and col-id.
     */
    String getCellText(String colId, int rowIndex);

    /**
     * Returns a {@link Locator} for a specific cell by col-id and row-index.
     */
    Locator cellLocator(String colId, int rowIndex);
}

