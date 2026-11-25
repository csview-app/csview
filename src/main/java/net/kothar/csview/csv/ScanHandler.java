package net.kothar.csview.csv;

public interface ScanHandler {

	void newCell(long cell, long row, int col, long pos);

	/**
	 * A row starts at {@code pos}. {@code previousCols} is the number of cells in the row that
	 * just ended, or 0 for the first row.
	 */
	void newRow(long cell, long row, int previousCols, long pos);

	/**
	 * The input has ended; {@code lastRowCols} is the number of cells in its last row, which
	 * no newRow call reports, since no row follows it. Called before notifyCompleted, and not if
	 * the scan was abandoned.
	 */
	default void endOfInput(int lastRowCols) {
	}

	void notifyCompleted();

}
