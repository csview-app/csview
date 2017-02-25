package net.kothar.csview.ui.search;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

import net.kothar.csview.csv.CSV;
import net.kothar.csview.csv.Index;

public class SearchIndexLabelProvider implements ITableLabelProvider {

	private CSV csv;
	private Index index;

	public SearchIndexLabelProvider(CSV csv, Index index) {
		this.csv = csv;
		this.index = index;
	}

	@Override
	public void addListener(ILabelProviderListener arg0) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public boolean isLabelProperty(Object arg0, String arg1) {
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener arg0) {
	}

	@Override
	public Image getColumnImage(Object arg0, int arg1) {
		return null;
	}

	@Override
	public String getColumnText(Object element, int col) {
		int row = (int) element;
		// Assume col is always 1;
		
		if (element == null) {
			return "";
		}
		
		String cell = csv.getCellAt(index.getPosition(row));
		return cell;
	}

}
