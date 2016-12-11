package net.kothar.csview.ui;

import net.kothar.csview.csv.CSV;
import net.kothar.csview.grid.IGridContentProvider;

public class CSVContentProvider implements IGridContentProvider {

	private CSV csv;

	public CSVContentProvider(CSV csv) {
		this.csv = csv;
	}

	public static class Row {
		public int row;
		public String[] values;
		
		public Row(int row, String[] values) {
			this.row = row;
			this.values = values;
		}
	}

	@Override
	public Object getRow(int index) {
		return new Row(index, csv.getRow(index));
	}

}
