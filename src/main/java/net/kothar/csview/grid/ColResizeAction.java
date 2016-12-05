package net.kothar.csview.grid;

import java.util.Map.Entry;

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
		// TODO directly ask for labels of all visible cells in the column
		for (Entry<Point, String> entry: grid.labelCache.asMap().entrySet()) {
			if (entry.getKey().x != column) {
				continue;
			}
			
			Point extent = gc.stringExtent(entry.getValue());
			if (extent.x > desiredSize) {
				desiredSize = extent.x + grid.getHorizontalCellPadding() * 2;
			}
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
