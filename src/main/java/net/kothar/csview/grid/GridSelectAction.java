package net.kothar.csview.grid;

import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;

public class GridSelectAction implements MouseAction {

	private Grid grid;
	private int col;
	private int row;

	public GridSelectAction(Grid grid, int col, int row) {
		this.grid = grid;
		this.col = col;
		this.row = row;
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseDown(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseUp(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseMove(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public Cursor getCursor(MouseEvent e) {
		// TODO Auto-generated method stub
		return null;
	}

}
