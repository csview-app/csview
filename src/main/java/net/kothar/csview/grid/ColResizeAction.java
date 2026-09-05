package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;

public class ColResizeAction implements MouseAction {

    private static final int MAX_COL_WIDTH = 4_000;
    private Grid grid;
	private int column;
	
	private int originalSize;
	private int originalX;
	private int lastDelta = 0;

	public ColResizeAction(Grid grid, int column) {
		this.grid = grid;
		this.column = column;
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
		int firstRow = firstVisibleRow();
		int desiredSize;

		GC gc = new GC(grid.canvas);
		try {
			desiredSize = grid.measureColumn(gc, column, firstRow, visibleRows(firstRow));
		} finally {
			gc.dispose();
		}

		if (desiredSize > MAX_COL_WIDTH) {
		    desiredSize = MAX_COL_WIDTH;
        }
		
		grid.setColumnSize(column, desiredSize);
	}

	/** The topmost row on screen, or 0 while there is nothing to show. */
	private int firstVisibleRow() {
		int yOffset = grid.getYOffset();
		if (grid.rows.getCount() == 0 || yOffset >= grid.rows.getTotal()) {
			return 0;
		}

		return grid.rows.getItemAt(yOffset);
	}

	/**
	 * Counts the rows on screen from {@code firstRow} down, so a double click fits the column to
	 * what the reader can actually see rather than to the whole file.
	 */
	private int visibleRows(int firstRow) {
		int yOffset = grid.getYOffset();
		int height = grid.canvas.getBounds().height;
		int rowCount = grid.rows.getCount();

		int row = firstRow;
		while (row < rowCount && grid.rows.getPosition(row) - yOffset < height) {
			row++;
		}

		return row - firstRow;
	}

	@Override
	public void mouseDown(MouseEvent e) {
		originalSize = grid.cols.getSize(column);
		originalX = e.x;
	}

	@Override
	public void mouseUp(MouseEvent e) {
		// Nothing to do
	}

	@Override
	public void mouseMove(MouseEvent e) {
		int newX = e.x;
		int delta = newX - originalX;
		if (delta == lastDelta)
			return;
		
		int newSize = originalSize + delta;
		if (newSize < 0) {
			newSize = 0;
		}
		grid.setColumnSize(column, newSize);
		lastDelta = delta;
	}

	@Override
	public Cursor getCursor(MouseEvent e) {
		return e.display.getSystemCursor(SWT.CURSOR_SIZEWE);
	}

}
