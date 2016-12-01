package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;

public class CSVTable extends Table {

	public CSVTable(Composite parent, int style) {
		super(parent, style);
	}
	
	@Override
	public void clear(int index) {
		checkWidget ();
		if (!(0 <= index && index < itemCount)) error (SWT.ERROR_INVALID_RANGE);
		TableItem item = items [index];
		if (item != null) {
			if (currentItem != item) {
				items[index] = null;
			}
		}
	}

}
