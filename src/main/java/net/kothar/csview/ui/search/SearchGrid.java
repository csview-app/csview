package net.kothar.csview.ui.search;

import org.eclipse.swt.widgets.Composite;

import net.kothar.csview.grid.Grid;
import net.kothar.csview.grid.MouseHandler;

public class SearchGrid extends Grid {

	public SearchGrid(Composite parent, int style) {
		super(parent, style);
	}

	@Override
	protected MouseHandler createMouseHandler() {
		return new SearchMouseHandler(this);
	}
}
