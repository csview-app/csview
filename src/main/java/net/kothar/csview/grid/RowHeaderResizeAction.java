package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;

public class RowHeaderResizeAction implements MouseAction {

	private Grid grid;
	
	private int originalSize;
	private int originalX;

	public RowHeaderResizeAction(Grid grid) {
		this.grid = grid;
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void mouseDown(MouseEvent e) {
		originalSize = grid.getRowHeaderSize();
		originalX = e.x;
	}

	@Override
	public void mouseUp(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseMove(MouseEvent e) {
		int newX = e.x;
		int delta = newX - originalX;
		int newSize = originalSize + delta;
		if (newSize < 0) {
			newSize = 0;
		}
		grid.setRowHeaderSize(newSize);
	}

	@Override
	public Cursor getCursor(MouseEvent e) {
		return e.display.getSystemCursor(SWT.CURSOR_SIZEWE);
	}

}
