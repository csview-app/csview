package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Rectangle;

public class ColSelectAction implements MouseAction {

	private Grid		grid;
	private int			startColumn;
	private Rectangle	selection;
	private int			lastColumn;

	public ColSelectAction(Grid grid, int column) {
		this.grid = grid;
		this.startColumn = column;
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
	}

	@Override
	public void mouseDown(MouseEvent e) {
		if ((e.stateMask & SWT.MODIFIER_MASK) == 0) {
			grid.getSelection().clear();
		}
		selectColumn(startColumn);
	}

	private void selectColumn(int column) {
		int from = column >= startColumn ? startColumn : column;
		int to = column >= startColumn ? column : startColumn;

		if (selection != null) {
			grid.getSelection().removeRegion(selection);
		}

		selection = new Rectangle(from, 0, to - from + 1, grid.getRowCount());
		grid.getSelection().addRegion(selection);
		grid.refresh();

		lastColumn = column;
	}

	@Override
	public void mouseUp(MouseEvent e) {
	}

	@Override
	public void mouseMove(MouseEvent e) {
		int thisCol = grid.getColAt(e.x);
		if (thisCol != lastColumn) {
			selectColumn(thisCol);
		}
	}

	@Override
	public Cursor getCursor(MouseEvent e) {
		if (selection == null) {
			return null;
		}

		return e.display.getSystemCursor(SWT.CURSOR_SIZEWE);
	}

}
