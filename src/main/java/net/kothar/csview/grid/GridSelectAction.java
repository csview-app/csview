package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

public class GridSelectAction implements MouseAction {

	private Grid grid;
	private int col;
	private int row;
	
	private Rectangle lastSelection;
	private boolean newSelection;
	
	public GridSelectAction(Grid grid, int col, int row) {
		this.grid = grid;
		this.col = col;
		this.row = row;
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	private Point getCell(MouseEvent e) {
		int x = getColAt(e.x - grid.getRowHeaderSize() + grid.getXOffset());
		int y = getRowAt(e.y - grid.getColumnHeaderSize() + grid.getYOffset());
		return new Point(x, y);
	}
	
	private int getRowAt(int y) {
		if (y < 0)
			return -1;
		else if (y >= grid.rows.getTotal())
			return grid.rows.getCount();
		return grid.rows.getItemAt(y);
	}

	private int getColAt(int x) {
		if (x < 0)
			return -1;
		else if (x >= grid.cols.getTotal())
			return grid.cols.getCount();
		return grid.cols.getItemAt(x);
	}
	
	@Override
	public void mouseDown(MouseEvent e) {
		if ((e.stateMask & SWT.MOD1) == 0) {
			System.out.println("Clear existing selection");
			grid.selection.clear();
			grid.tileCache.invalidateAll();
		}
		lastSelection = new Rectangle(col, row, 1, 1);
		newSelection = grid.selection.addRegion(lastSelection);
		grid.setCurrentCell(new Point(col, row));
		grid.invalidateTiles(lastSelection);
	}

	@Override
	public void mouseUp(MouseEvent e) {
		// Handle single cell toggle
		Point cell = getCell(e);
		if (!newSelection && col == cell.x && row == cell.y && (e.stateMask & SWT.MOD1) != 0) {
			System.out.println("Toggle selection off");
			grid.selection.removeRegion(lastSelection);
			grid.invalidateTiles(lastSelection);
		}
	}

	@Override
	public void mouseMove(MouseEvent e) {
		Point cell = getCell(e);
		Rectangle selection = new Rectangle(col, row, cell.x - col + 1, cell.y - row + 1);
		if (!selection.equals(lastSelection)) {
			System.out.println("Selection updated: " + selection);
			grid.selection.removeRegion(lastSelection);
			grid.selection.addRegion(selection);
			grid.setCurrentCell(cell);
			grid.invalidateTiles(lastSelection, selection);
			lastSelection = selection;
		}
	}

	@Override
	public Cursor getCursor(MouseEvent e) {
		return null;
	}

}
