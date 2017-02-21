package net.kothar.csview.ui;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

import net.kothar.csview.csv.CSV;
import net.kothar.csview.ui.CSVContentProvider.Row;

public class CSVLabelProvider implements ITableLabelProvider {

	private CSV csv;

	public CSVLabelProvider(CSV csv) {
		this.csv = csv;
	}
	
	@Override
	public void addListener(ILabelProviderListener listener) {
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	@Override
	public String getColumnText(Object element, int column) {
		Row row = (Row) element;
		
		String cell = csv.getCell(row.row, column);
		return cell == null ? "" : cell;
	}

	@Override
	public Image getColumnImage(Object element, int column) {
		return null;
	}

	@Override
	public void dispose() {
	}

}
