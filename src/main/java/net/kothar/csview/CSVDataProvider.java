/* Copyright 2016 Kothar Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
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
