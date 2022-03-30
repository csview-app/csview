package net.kothar.csview.ui.csv;

import net.kothar.csview.grid.Grid;
import net.kothar.csview.grid.MouseHandler;
import org.eclipse.swt.widgets.Composite;

public class CSVGrid extends Grid {

	public CSVGrid(Composite parent, int style) {
		super(parent, style);
	}

	@Override
	protected MouseHandler createMouseHandler() {
		return new CSVMouseHandler(this);
	}

}
