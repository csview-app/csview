/* Copyright 2016 Kothar Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package net.kothar.csview;

import java.io.File;
import java.io.FileNotFoundException;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.StatusLineContributionItem;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.CSVTable;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;


public class CSView extends ApplicationWindow implements DocumentActions {
	
	private static Image appIcon;
	
	public static Image getAppIcon() {
		if (appIcon == null) {
			appIcon = new Image(Display.getDefault(), CSView.class.getResourceAsStream("/icon.png"));
		}
		return appIcon;
	}

	private CSV csv;
	private String file;
	private ProgressBar progressBar;
	private Composite progressRow;

	private boolean useAppIcon;
	
	private Table table;
	private TableViewer viewer;

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
	
	public CSView() {
		super(null);

		addStatusLine();
	}

	public CSView(String[] args) {
		this();
		
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

	public CSView(File file) {
		this();
		
		csv = new CSV();
		
		try {
			loadCSV(file.toString());
		} catch (FileNotFoundException e) {
			loadCSVString(e.getMessage());
		}
	}
	
	public void useAppIcon() {
		useAppIcon = true;
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
	public void addMenuBar() {
		super.addMenuBar();
	}
	
	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);

		shell.setSize(1024, 768);
		if (useAppIcon)
			shell.setImage(getAppIcon());

		if (file != null) {
			shell.setText(file + " - CSView");
		} else {
			shell.setText("CSView");
		}
	}

	@Override
	protected Control createContents(Composite parent) {
		
		Composite composite = (Composite) super.createContents(parent);
		
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		composite.setLayout(layout);
		
		table = new CSVTable(composite, SWT.VIRTUAL);
		table.setLayoutData(new GridData(GridData.FILL_BOTH));
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		
		viewer = new TableViewer(table);
		viewer.setContentProvider(new CSVContentProvider());
		viewer.setLabelProvider(new CSVLabelProvider());
		viewer.setInput(csv);
		
		TableColumn column = new TableColumn(table, SWT.RIGHT, 0);
		column.setText("");
		column.setWidth(80);
		
		for (int i = 1; i <= 10; i++) {
			column = new TableColumn(table, SWT.LEFT, i);
			column.setText("");
			column.setWidth(200);
		}
		
		if (file != null) {
			// Add a progress indicator
			progressRow = new Composite(composite, SWT.NORMAL);
			progressRow.setLayout(new GridLayout(2, false));
			progressRow.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			new Label(progressRow, SWT.NORMAL).setText("Scanning...");
			progressBar = new ProgressBar(progressRow, SWT.SMOOTH | SWT.HORIZONTAL);
			progressBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	
			long fileSize = new File(file).length();
			progressBar.setMaximum(1000);
			progressBar.setSelection(0);
			csv.addProgressListener(new ProgressListener() {
				
				@Override
				public void completed() {
					getShell().getDisplay().asyncExec(() -> {
						progressRow.dispose();
						composite.layout(true);
						refreshTable();
					});
				}
				
				@Override
				public void changed(long progress) {
					getShell().getDisplay().asyncExec(() -> {
						progressBar.setSelection((int) ((progress*1000)/fileSize));
						refreshTable();
					});
				}
				
			});
		}

		// Load the CSV
		csv.scan();
		
		getShell().getDisplay().asyncExec(this::refreshTable);
		
		getShell().addDisposeListener(csv::dispose);

		return composite;
	}
	
	protected StatusLineManager createStatusLineManager() {
        StatusLineManager statusLineManager = super.createStatusLineManager();
        
        StatusLineContributionItem message = new StatusLineContributionItem("message");
        message.setText("CSView");
		statusLineManager.add(message);
        
        Action action = new Action("Test action") {
		};
		statusLineManager.add(action);
        
		statusLineManager.update(true);
        return statusLineManager;
    }

	public void refreshTable() {
		viewer.setItemCount(csv.getRowCount());
	}

}
