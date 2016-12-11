package net.kothar.csview.ui;

import net.kothar.csview.csv.CSV;

public class CSVColumnHeaderProvider extends NumberFormatLabelProvider {

	private CSV csv;

	public CSVColumnHeaderProvider(CSV csv) {
		super(1);
		this.csv = csv;
	}

	@Override
	public String getText(Object element) {
		int col = ((Number) element).intValue();
		
		String[] row = csv.getRow(0);
		if (col >= row.length)
			return super.getText(element);
		
		return row[col];
	}

}
