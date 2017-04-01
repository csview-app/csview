package net.kothar.csview.ui.search;

import static net.kothar.csview.ui.Adapters.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import net.kothar.csview.ProgressListener;
import net.kothar.csview.csv.CSV;
import net.kothar.csview.csv.Index;
import net.kothar.csview.grid.Grid;

public class SearchSidebar extends Composite implements ISelectionProvider {

	private Text search;
	private Grid grid;
	private Button close;
	private Timer debounce;

	private CSV csv;
	private Index index;
	private StructuredSelection selection;
	private List<ISelectionChangedListener> listeners = new ArrayList<>();

	public SearchSidebar(Composite parent, CSV csv) {
		super(parent, SWT.BORDER);
		this.csv = csv;

		createContents();
	}

	protected void createContents() {
		setLayout(new GridLayout(2, false));

		Label label = new Label(this, SWT.NORMAL);
		label.setText("Search");
		label.setLayoutData(new GridData(GridData.BEGINNING, GridData.BEGINNING, false, false, 1, 1));

		close = new Button(this, SWT.ARROW | SWT.RIGHT);
		close.setText("Close");
		close.setLayoutData(new GridData(GridData.END, GridData.END, false, false, 1, 1));

		search = new Text(this, SWT.NORMAL);
		search.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 2, 1));
		search.addModifyListener(this::handleModify);

		grid = new SearchGrid(this, SWT.BORDER);
		grid.addCurrentCellListener(this::selectResult);
		grid.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1));
		grid.setColumnSize(0, 400);
		
		LabelProvider emptyLabelProvider = new LabelProvider() {
			@Override
			public String getText(Object element) {
				return "";
			}
		};
		grid.setColumnLabelProvider(emptyLabelProvider);
		grid.setRowLabelProvider(emptyLabelProvider);
	}

	public void handleModify(ModifyEvent e) {
		if (debounce != null) {
			debounce.cancel();
		}
		debounce = new Timer(true);
		debounce.schedule(displayTask(getDisplay(), this::updateSearch), 500);
	}

	private void updateSearch() {
		grid.setRows(0);
		grid.setXOffset(0);
		grid.setYOffset(0);

		String searchString = search.getText().trim();
		if (searchString.isEmpty()) {
			return;
		}

		System.out.println("Search for '" + searchString + "'");
		index = csv.search(searchString, new ProgressListener() {
			@Override
			public void completed() {
				refreshGrid();
			}

			@Override
			public void columnsChanged(int columns) {
			}

			@Override
			public void changed() {
				refreshGrid();
			}
		});

		grid.setContentProvider(new SearchIndexContentProvider(index));
		grid.setLabelProvider(new SearchIndexLabelProvider(csv, index));
		grid.setColumnLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return index.size() + " results for " + searchString;
			}
		});
		grid.setRowLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				Integer result = (Integer) element;
				Long position = index.getPosition(result);
				Point p = csv.getPoint(position);
				String[] header = csv.getRow(0);
				String colLabel;
				if (header.length > p.x) {
					colLabel = header[p.x];
				} else {
					colLabel = Integer.toString(p.x + 1);
				}
				return String.format("%s:%d", colLabel, p.y + 1);
			}
		});
	}

	protected void refreshGrid() {
		getDisplay().asyncExec(() -> grid.setRows(index.size()));
	}

	public void addCloseListener(SelectionListener listener) {
		close.addSelectionListener(listener);
	}

	public void focusInput() {
		search.setFocus();
	}
	
	public void selectResult(Point cell) {
		Long cellPos = index.getPosition(cell.y);
		Point originalCell = csv.getPoint(cellPos);
		selection = new StructuredSelection(originalCell);
		for (ISelectionChangedListener listener: listeners) {
			listener.selectionChanged(new SelectionChangedEvent(this, selection));
		}
	}

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		listeners.add(listener);
	}

	@Override
	public ISelection getSelection() {
		return selection;
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		listeners.remove(listener);
	}

	@Override
	public void setSelection(ISelection selection) {
		// Not implemented
	}

}
