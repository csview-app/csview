package net.kothar.csview;

import org.eclipse.jface.viewers.ILazyContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;

public class CSVContentProvider implements ILazyContentProvider {

	private CSV csv;
	private TableViewer viewer;

	public CSVContentProvider() {
	}

	@Override
	public void dispose() {
		
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		this.viewer = (TableViewer) viewer;
		csv = (CSV) newInput;
	}

	@Override
	public void updateElement(int index) {
		viewer.replace(new Row(index, csv.getRow(index)), index);
	}

	public static class Row {
		public int row;
		public String[] values;
		
		public Row(int row, String[] values) {
			this.row = row;
			this.values = values;
		}
	}

}
