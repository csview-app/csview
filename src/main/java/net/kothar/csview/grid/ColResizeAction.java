package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;

public class ColResizeAction implements MouseAction {

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
		int desiredSize = 5;
		GC gc = new GC(e.display);
		
		int yOffset = grid.getYOffset();
		int row = grid.rows.getItemAt(yOffset);
		int y = grid.rows.getPosition(row);
		int height = grid.canvas.getBounds().height;
		int rowCount = grid.rows.getCount();
		
		while (y - yOffset < height) {
			String label = grid.getLabel(column, row);
			
			Point extent = gc.stringExtent(label);
			if (extent.x > desiredSize) {
				desiredSize = extent.x + grid.getHorizontalCellPadding() * 2;
			}
			
			if (++row >= rowCount) {
				break;
			}
			
			y = grid.rows.getPosition(row);
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
