package net.kothar.csview.ui.csv;

import net.kothar.csview.grid.ColSelectAction;
import net.kothar.csview.grid.Grid;
import net.kothar.csview.grid.MouseAction;
import net.kothar.csview.grid.MouseHandler;
import net.kothar.csview.grid.RowSelectAction;

public class CSVMouseHandler extends MouseHandler {

	public CSVMouseHandler(Grid grid) {
		super(grid);
	}

	@Override
	protected MouseAction createColumnHeaderAction(Grid grid, int colIndex) {
		if (colIndex < 0) {
			return null;
		}
		return new ColSelectAction(grid, colIndex);
	}

	@Override
	protected MouseAction createRowHeaderAction(Grid grid, int rowIndex) {
		if (rowIndex < 0) {
			return null;
		}
		return new RowSelectAction(grid, rowIndex);
	}

}
