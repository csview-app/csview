package net.kothar.csview.csv;

public interface ScanHandler {

	void newCell(long cell, long row, int col, long pos);

	void newRow(long cell, long row, int previousCols, long pos);

	void notifyCompleted();

}
