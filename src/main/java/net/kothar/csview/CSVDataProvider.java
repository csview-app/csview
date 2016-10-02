package net.kothar.csview;

import org.eclipse.nebula.widgets.nattable.data.IDataProvider;

public class CSVDataProvider implements IDataProvider {

	private CSV csv;
	private int maxColumns = 1;

	public CSVDataProvider(CSV csv) {
		this.csv = csv;
	}

	@Override
	public int getColumnCount() {
		return maxColumns;
	}

	@Override
	public Object getDataValue(int columnIndex, int rowIndex) {
		String[] row = csv.getRow(rowIndex);
		if (row.length > maxColumns)
			maxColumns = row.length;
		
		if (columnIndex >= row.length)
			return null;
		return row[columnIndex];
	}

	@Override
	public void setDataValue(int columnIndex, int rowIndex, Object newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getRowCount() {
		return csv.getRowCount();
	}

}
