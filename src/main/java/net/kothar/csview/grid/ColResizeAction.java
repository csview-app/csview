package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;

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
		GC gc = new GC(e.display);
		int desiredSize = 5;

		int horizontalCellPadding = grid.getHorizontalCellPadding();
		if (grid.colLabelProvider != null) {
			String label = grid.colLabelProvider.getText(column);
			Point extent = gc.stringExtent(label);
			desiredSize = extent.x + horizontalCellPadding * 2;
		}
		
		int yOffset = grid.getYOffset();
		int row = grid.rows.getItemAt(yOffset);
		int y = grid.rows.getPosition(row);
		int height = grid.canvas.getBounds().height;
		int rowCount = grid.rows.getCount();
		
		while (y - yOffset < height) {
			String label = grid.getLabel(column, row);
			Point extent = gc.stringExtent(label);
			
			int labelSize = extent.x + horizontalCellPadding * 2;
			if (labelSize > desiredSize) {
				desiredSize = labelSize;
			}
			
			if (++row >= rowCount)
				break;
			y = grid.rows.getPosition(row);
		}

		if (desiredSize > MAX_COL_WIDTH) {
		    desiredSize = MAX_COL_WIDTH;
        }
		
		grid.setColumnSize(column, desiredSize);
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
