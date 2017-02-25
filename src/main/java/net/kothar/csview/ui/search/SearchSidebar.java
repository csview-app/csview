package net.kothar.csview.ui.search;

import static net.kothar.csview.ui.Adapters.*;

import java.util.Timer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.SelectionListener;
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

public class SearchSidebar extends Composite {

	private Text search;
	private Grid grid;
	private Button close;
	private Timer debounce;
	
	private CSV csv;
	private Index index;

	public SearchSidebar(Composite parent, CSV csv) {
		super(parent, SWT.BORDER);
		this.csv = csv;
		
		createContents();
	}

	protected void createContents() {
		setLayout(new GridLayout(2, false));
		
		Label label = new Label(this, SWT.NORMAL);
		label.setText("Search");
		label.setLayoutData(new GridData(GridData.BEGINNING, GridData.BEGINNING, false, false, 2, 1));
		
		search = new Text(this, SWT.NORMAL);
		search.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		search.addModifyListener(this::handleModify);
		
		grid = new Grid(this, SWT.BORDER);
		grid.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1));
		
		close = new Button(this, SWT.NORMAL);
		close.setText("Cancel");
		close.setLayoutData(new GridData(GridData.END, GridData.END, false, false, 2, 1));
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
		
		String searchString = search.getText();
		if (searchString.isEmpty()) {
			return;
		}
		
		System.out.println("Search for " + search.getText());
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
	
}
