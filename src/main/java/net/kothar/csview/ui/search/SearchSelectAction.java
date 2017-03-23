package net.kothar.csview.ui.search;

import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;

import net.kothar.csview.grid.Grid;
import net.kothar.csview.grid.MouseAction;

public class SearchSelectAction implements MouseAction {

	private Grid grid;
	private int col;
	private int row;

	public SearchSelectAction(Grid grid, int col, int row) {
		this.grid = grid;
		this.col = col;
		this.row = row;
	}
	
	@Override
	public void mouseUp(MouseEvent e) {
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
	}

	@Override
	public void mouseDown(MouseEvent e) {
		grid.setCurrentCell(new Point(col, row));
	}

	@Override
	public void mouseMove(MouseEvent e) {
	}

	@Override
	public Cursor getCursor(MouseEvent e) {
		return null;
	}

}
