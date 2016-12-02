package net.kothar.csview.ui;

import java.text.NumberFormat;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

import net.kothar.csview.ui.CSVContentProvider.Row;

public class CSVLabelProvider implements ITableLabelProvider {
	
	NumberFormat rowFormat = NumberFormat.getIntegerInstance();
	
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
		
		if (column == 0) {
			return rowFormat.format(row.row + 1);
		}
		
		if (column <= row.values.length)
			return row.values[column - 1];
		else 
			return "";
	}

	@Override
	public Image getColumnImage(Object element, int column) {
		return null;
	}

	@Override
	public void dispose() {
	}

}
