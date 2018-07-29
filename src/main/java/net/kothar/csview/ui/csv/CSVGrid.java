package net.kothar.csview.ui.csv;

import org.eclipse.swt.widgets.Composite;

import net.kothar.csview.grid.Grid;
import net.kothar.csview.grid.MouseHandler;

public class CSVGrid extends Grid {

	public CSVGrid(Composite parent, int style) {
		super(parent, style);
	}

	@Override
	protected MouseHandler createMouseHandler() {
		return new CSVMouseHandler(this);
	}

}
