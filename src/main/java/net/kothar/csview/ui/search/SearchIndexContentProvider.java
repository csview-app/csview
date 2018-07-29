package net.kothar.csview.ui.search;

import net.kothar.csview.csv.Index;
import net.kothar.csview.grid.IGridContentProvider;

public class SearchIndexContentProvider implements IGridContentProvider {

	private Index index;

	public SearchIndexContentProvider(Index index) {
		this.index = index;
	}

	@Override
	public Object getRow(int index) {
		return index;
	}

}
