package net.kothar.csview.ui;

import java.util.TreeSet;

import org.eclipse.jface.viewers.ILazyContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Table;

import net.kothar.csview.csv.CSV;

public class CSVContentProvider implements ILazyContentProvider {

	private static final int PURGE_AFTER_RENDER_COUNT = 1000;
	
	private CSV csv;
	private TableViewer viewer;
	private TreeSet<Integer> rendered;

	public CSVContentProvider() {
	}

	@Override
	public void dispose() {
		
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		this.viewer = (TableViewer) viewer;
		csv = (CSV) newInput;
		rendered = new TreeSet<>();
	}

	@Override
	public void updateElement(int index) {
		if (csv == null) {
			// Nothing to do
			return;
		}
		
		viewer.replace(new Row(index, csv.getRow(index)), index);
		rendered.add(index);
		
		if (rendered.size() >= PURGE_AFTER_RENDER_COUNT) {
			Table table = viewer.getTable();
			int top = table.getTopIndex() - 10;
			for (Integer i: rendered.headSet(top)) {
				viewer.clear(i);
			}
			int bottom = table.getTopIndex() + table.getSize().y / table.getItemHeight() + 10;
			for (Integer i: rendered.tailSet(bottom)) {
				viewer.clear(i);
			}
			rendered = new TreeSet<>(rendered.subSet(top, bottom));
		}
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
