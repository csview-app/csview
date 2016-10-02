package net.kothar.csview;

import java.io.FileNotFoundException;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.data.DefaultCornerDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.data.DefaultRowHeaderDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.layer.ColumnHeaderLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.CornerLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.GridLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.RowHeaderLayer;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.layer.ILayer;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.viewport.ViewportLayer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;


public class CSView extends ApplicationWindow {

	private CSV csv;
	private String file;

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

		// Body stack
	    CSVDataProvider bodyDataProvider = new CSVDataProvider(csv);
		DataLayer bodyDataLayer = new DataLayer(bodyDataProvider);
	    SelectionLayer selectionLayer = new SelectionLayer(bodyDataLayer);
	    ViewportLayer viewportLayer = new ViewportLayer(selectionLayer);
	    
	    // Column header stack
	    CSVHeaderProvider columnHeaderDataProvider = new CSVHeaderProvider(csv);
		DataLayer headerDataLayer = new DataLayer(columnHeaderDataProvider);
	    ILayer columnHeaderLayer = new ColumnHeaderLayer(headerDataLayer,
	        viewportLayer, 
	        selectionLayer);
	    
	    // Create row header stack
	    IDataProvider rowHeaderDataProvider = new DefaultRowHeaderDataProvider(bodyDataProvider);
	    DataLayer rowHeaderDataLayer = new DataLayer(rowHeaderDataProvider, 40, 20);
	    ILayer rowHeaderLayer = new RowHeaderLayer(rowHeaderDataLayer, 
	        viewportLayer, 
	        selectionLayer);

	    // Create corner stack
	    ILayer cornerLayer = 
	         new CornerLayer(new DataLayer(new DefaultCornerDataProvider(
	        		 columnHeaderDataProvider, rowHeaderDataProvider)), 
	            rowHeaderLayer, 
	            columnHeaderLayer);
	    
	    GridLayer gridLayer = 
	            new GridLayer(viewportLayer, columnHeaderLayer, rowHeaderLayer, cornerLayer);
	    
	    final NatTable natTable = new NatTable(parent, gridLayer);
	    GridDataFactory.fillDefaults().grab(true, true).applyTo(natTable);
	    
	    getShell().getDisplay().asyncExec(natTable::refresh);
	    
	    return natTable;
	}
}
