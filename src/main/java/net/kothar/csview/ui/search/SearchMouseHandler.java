package net.kothar.csview.ui.search;

import net.kothar.csview.grid.Grid;
import net.kothar.csview.grid.MouseAction;
import net.kothar.csview.grid.MouseHandler;

public class SearchMouseHandler extends MouseHandler {

	public SearchMouseHandler(Grid grid) {
		super(grid);
	}

	@Override
	protected MouseAction createCellAction(int colIndex, int rowIndex) {
		return new SearchSelectAction(grid, colIndex, rowIndex);
	}
}
