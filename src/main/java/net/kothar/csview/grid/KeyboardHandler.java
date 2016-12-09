package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

public class KeyboardHandler implements KeyListener {

	private Grid grid;

	public KeyboardHandler(Grid grid) {
		this.grid = grid;
	}

	@Override
	public void keyPressed(KeyEvent e) {
		switch (e.keyCode) {
		case SWT.ARROW_UP:
			moveCursor(e, 0, -1);
			break;
		case SWT.ARROW_DOWN:
			moveCursor(e, 0, 1);
			break;
		case SWT.ARROW_LEFT:
			moveCursor(e, -1, 0);
			break;
		case SWT.ARROW_RIGHT:
			moveCursor(e, 1, 0);
			break;
		}
	}

	private void moveCursor(KeyEvent e, int cols, int rows) {
		Point cell = grid.getCurrentCell();
		Point newCell = new Point(cell.x + cols, cell.y + rows);
		if (grid.getCellBounds().contains(newCell)) {
			grid.setCurrentCell(newCell);
			
			Rectangle cellRegion = new Rectangle(newCell.x, newCell.y, 1, 1);
			if ((e.stateMask & (SWT.MOD1 | SWT.MOD2)) != 0) {
				Rectangle lastRegion = grid.selection.getLastRegion();
				if (lastRegion != null) {
					Rectangle newRegion = lastRegion.union(cellRegion);
					grid.selection.removeRegion(lastRegion);
					grid.selection.addRegion(newRegion);
					grid.invalidateTiles(lastRegion, newRegion);
				} else {
					grid.invalidateTiles(grid.selection.selectedRegions);
					grid.selection.clear();
					grid.selection.addRegion(cellRegion);
					grid.invalidateTiles(cellRegion);
				}
			} else {
				grid.invalidateTiles(grid.selection.selectedRegions);
				grid.invalidateTiles(cellRegion);
				grid.selection.clear();
				grid.selection.addRegion(cellRegion);
			}
			
			grid.redraw();
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub

	}

}
