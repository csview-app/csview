package net.kothar.csview;

import java.text.NumberFormat;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

import net.kothar.csview.CSVContentProvider.Row;

public class CSVLabelProvider implements ITableLabelProvider {
	
	NumberFormat rowFormat = NumberFormat.getIntegerInstance();

	@Override
	public void addListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub
		
	}

}
