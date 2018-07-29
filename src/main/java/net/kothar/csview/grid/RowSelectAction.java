package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Rectangle;

public class RowSelectAction implements MouseAction {

	private Grid		grid;
	private int			startRow;
	private Rectangle	selection;
	private int			lastRow;

	public RowSelectAction(Grid grid, int row) {
		this.grid = grid;
		this.startRow = row;
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
	}

	@Override
	public void mouseDown(MouseEvent e) {
		if ((e.stateMask & SWT.MODIFIER_MASK) == 0) {
			grid.getSelection().clear();
		}
		selectRow(startRow);
	}

	private void selectRow(int row) {
		int from = row >= startRow ? startRow : row;
		int to = row >= startRow ? row : startRow;

		if (selection != null) {
			grid.getSelection().removeRegion(selection);
		}

		selection = new Rectangle(0, from, grid.getColCount(), to - from + 1);
		grid.getSelection().addRegion(selection);
		grid.refresh();

		lastRow = row;
	}

	@Override
	public void mouseUp(MouseEvent e) {
	}

	@Override
	public void mouseMove(MouseEvent e) {
		int thisRow = grid.getRowAt(e.y);
		if (thisRow != lastRow) {
			selectRow(thisRow);
		}
	}

	@Override
	public Cursor getCursor(MouseEvent e) {
		if (selection == null) {
			return null;
		}

		return e.display.getSystemCursor(SWT.CURSOR_SIZENS);
	}

}
