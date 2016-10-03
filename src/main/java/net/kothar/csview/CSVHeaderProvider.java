package net.kothar.csview;

import org.eclipse.nebula.widgets.nattable.data.IDataProvider;

public class CSVHeaderProvider implements IDataProvider {

	private CSV csv;

	public CSVHeaderProvider(CSV csv) {
		this.csv = csv;
	}

	@Override
	public Object getDataValue(int columnIndex, int rowIndex) {
		String[] row = csv.getRow(0);
		if (columnIndex < 0 || columnIndex > row.length) {
			return null;
		}
		return row[columnIndex];
	}

	@Override
	public void setDataValue(int columnIndex, int rowIndex, Object newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getColumnCount() {
		return csv.getRow(0).length;
	}

	@Override
	public int getRowCount() {
		return 1;
	}

}
