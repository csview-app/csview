package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Rectangle;

public class ColSelectAction implements MouseAction {

	private Grid	grid;
	private int		column;

	public ColSelectAction(Grid grid, int column) {
		this.grid = grid;
		this.column = column;
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
	}

	@Override
	public void mouseDown(MouseEvent e) {
		Rectangle region = new Rectangle(column, 0, 1, grid.getRowCount());
		if ((e.stateMask & SWT.MODIFIER_MASK) == 0) {
			grid.getSelection().clear();
		}
		grid.getSelection().addRegion(region);
		grid.refresh();
	}

	@Override
	public void mouseUp(MouseEvent e) {
	}

	@Override
	public void mouseMove(MouseEvent e) {
	}

	@Override
	public Cursor getCursor(MouseEvent e) {
		return null;
	}

}
