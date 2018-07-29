package net.kothar.csview.ui.search;

import static net.kothar.csview.ui.Adapters.displayTask;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
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

	private Text	search;
	private Grid	grid;
	private Timer	debounce;

	private CSV								csv;
	private Index							index;
	private StructuredSelection				selection;
	private List<ISelectionChangedListener>	listeners	= new ArrayList<>();
	private Button							regular;
	private Button							caseInsensitive;

	private Label	message;
	private String	searchString;

	private SelectionListener updateSearch = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent e) {
			updateSearch();
		}
	};

	public SearchSidebar(Composite parent, CSV csv) {
		super(parent, SWT.BORDER);
		this.csv = csv;

		createContents();
	}

	protected void createContents() {
		setLayout(new GridLayout(2, false));

		regular = new Button(this, SWT.CHECK);
		regular.setText("Regular expression");
		regular.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 2, 1));
		regular.addSelectionListener(updateSearch);

		caseInsensitive = new Button(this, SWT.CHECK);
		caseInsensitive.setText("Case insensitive");
		caseInsensitive.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 2, 1));
		caseInsensitive.addSelectionListener(updateSearch);

		search = new Text(this, SWT.NORMAL);
		search.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 2, 1));
		search.addModifyListener(this::handleModify);

		message = new Label(this, SWT.BACKGROUND);
		message.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 2, 1));
		message.setForeground(getDisplay().getSystemColor(SWT.COLOR_TITLE_FOREGROUND));
		message.setBackground(getDisplay().getSystemColor(SWT.COLOR_TITLE_BACKGROUND_GRADIENT));
		message.setFont(JFaceResources.getTextFont());
		message.setVisible(false);
		message.setSize(0, 0);

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

		searchString = search.getText().trim();
		if (searchString.isEmpty()) {
			return;
		}

		System.out.println("Search for '" + searchString + "'");

		// Prepare the pattern to search for
		Pattern pattern;
		try {
			String escapedSearchString = searchString.replaceAll("\"", "\"\"");
			if (!regular.getSelection()) {
				escapedSearchString = Pattern.quote(escapedSearchString);
			}

			byte[] searchBytes = escapedSearchString.getBytes(csv.getCharset());
			int flags = caseInsensitive.getSelection() ? Pattern.CASE_INSENSITIVE : 0;
			pattern = Pattern.compile(new String(searchBytes, "ISO-8859-1"), flags);
		} catch (PatternSyntaxException e) {
			message.setText(e.getMessage());
			message.setVisible(true);
			layout(true);
			return;
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}

		message.setVisible(true);
		message.setText("Searching...");
		layout(true);

		index = csv.search(pattern, new ProgressListener() {
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

		grid.setHeaderVisible(false);
		grid.setContentProvider(new SearchIndexContentProvider(index));
		grid.setLabelProvider(new SearchIndexLabelProvider(csv, index));
		grid.setRowLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				Integer result = (Integer) element;
				Long position = index.getPosition(result);
				Point p = csv.getPoint(position);
				return String.format("%,d", p.y + 1);
			}
		});
	}

	protected void refreshGrid() {
		getDisplay().asyncExec(() -> {
			grid.setRows(index.size());
			message.setText(String.format("%,d results", index.size()));
		});
	}

	public void focusInput() {
		search.setFocus();
	}

	public void selectResult(Point cell) {
		Long cellPos = index.getPosition(cell.y);
		Point originalCell = csv.getPoint(cellPos);
		selection = new StructuredSelection(originalCell);
		for (ISelectionChangedListener listener : listeners) {
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
