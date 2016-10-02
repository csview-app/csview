package net.kothar.csview;

import java.io.FileNotFoundException;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.viewport.ViewportLayer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


public class CSView extends ApplicationWindow {

	private CSV csv;
	private String file;
	private Table table;

	public static void main(String[] args) {
		Display display = new Display();

		CSView csView = new CSView(args);
		csView.open();

		csView.getShell().addDisposeListener(e -> display.dispose());

		while (!display.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		
	}

	public CSView(String[] args) {
		super(null);
		if (args.length > 0) {
			csv = new CSV();
			
			try {
				loadCSV(args[0]);
			} catch (FileNotFoundException e) {
				loadCSVString(e.getMessage());
			}
		} else {
			loadCSVString("No Data, Please open a file");
		}
	}
	
	private void loadCSVString(String string) {
		csv = new CSV();
		csv.setContents(string);
	}

	private void loadCSV(String file) throws FileNotFoundException {
		setFile(file);
		csv.setFile(file);
	}

	private void setFile(String file) {
		this.file = file;
		Shell shell = getShell();
		if (shell != null) {
			shell.setText(file + " - CSView");
		}
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);

		shell.setSize(1024, 768);
		
		if (file != null) {
			shell.setText(file + " - CSView");
		} else {
			shell.setText("CSView");
		}
	}

	@Override
	protected Control createContents(Composite parent) {

	    final DataLayer bodyDataLayer = new DataLayer(new CSVDataProvider(csv));
	    SelectionLayer selectionLayer = new SelectionLayer(bodyDataLayer);
	    ViewportLayer viewportLayer = new ViewportLayer(selectionLayer); 
	    
	    viewportLayer.setRegionName(GridRegion.BODY);
	    
	    final NatTable natTable = new NatTable(parent, viewportLayer);
	    
	    return natTable;
	}

	private Control createSWTTable(Composite parent) {
		table = new Table(parent, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
		table.setItemCount(csv.getRowCount() - 1);
		table.setLinesVisible(true);
		
		final TableColumn rowCol = new TableColumn(table, SWT.NORMAL);
		rowCol.setText("#");
		rowCol.setWidth(20);
		
		table.addListener(SWT.SetData, new Listener() {
			public void handleEvent(Event event) {
				TableItem item = (TableItem) event.item;
				int index = table.indexOf(item);

				String[] row = csv.getRow(index + 1);
				String[] numberedRow = new String[row.length + 1];
				numberedRow[0] = Integer.toString(index + 1);
				System.arraycopy(row, 0, numberedRow, 1, row.length);
				
				for (int i = table.getColumnCount() - 1; i < row.length; i++) {
					addColumn(table, i);
				}
				item.setText(numberedRow);
				rowCol.pack();
			}
		});
		table.setHeaderVisible(true);

		csv.addRowListener(new RowListener() {
			
			private Timer timer;
			
			@Override
			public synchronized void rowAdded(int row) {
				if (timer == null) {
					timer = new Timer(true);
					timer.schedule(new TimerTask() {
						@Override
						public void run() {
							getShell().getDisplay().asyncExec(() -> table.setItemCount(csv.getRowCount() - 1));
							timer.cancel();
							timer = null;
						}
					}, 1000);
				}
			}
		});
		
		return table;
	}

	public void addColumn(final Table table, int index) {
		TableColumn col = new TableColumn(table, SWT.NORMAL);
		String[] row0 = csv.getRow(0);
		if (row0.length > index) {
			col.setText(row0[index]);
		} else {
			col.setText("[Column " + (index + 1) + "]");
		}
		col.setWidth(200);
	}
}
