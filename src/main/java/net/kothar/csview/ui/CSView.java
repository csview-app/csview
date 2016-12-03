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
package net.kothar.csview.ui;

import java.io.File;
import java.io.FileNotFoundException;

import org.apache.commons.csv.CSVFormat;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.StatusLineContributionItem;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.CSVTable;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import net.kothar.csview.DocumentActions;
import net.kothar.csview.ProgressListener;
import net.kothar.csview.csv.CSV;


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

		table = new CSVTable(composite, SWT.VIRTUAL | SWT.FULL_SELECTION);
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

		if (file != null) {
			IProgressMonitor progressMonitor = getStatusLineManager().getProgressMonitor();
			progressMonitor.beginTask("Scanning", 1000);

			long fileSize = new File(file).length();
			csv.addProgressListener(new ProgressListener() {

				private long lastProgress = 0;

				@Override
				public void completed() {
					getShell().getDisplay().asyncExec(() -> {
						progressMonitor.done();
						refreshTable();
					});
				}

				@Override
				public void changed(long progress) {
					getShell().getDisplay().asyncExec(() -> {
						int worked = ((int) (((progress - lastProgress)*1000)/fileSize));
						if (worked > 0) {
							progressMonitor.worked(worked);
							lastProgress = progress;
						}
						refreshTable();
					});
				}

				@Override
				public void columnsChanged(int columns) {
					String[] headerRow = csv.getRow(0);
					for (int i = table.getColumnCount()-1; i < columns; i++) {
						TableColumn column = new TableColumn(table, SWT.LEFT);
						if (i < headerRow.length)
							column.setText(headerRow[i]);
						else
							column.setText("[" + (i+1) + "]");
						column.setWidth(200);
					}
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

		createDelimiterMenu(statusLineManager);
		
		statusLineManager.add(new StatusLineContributionItem("quote", 10) {{
			setText("Escape: \"");
		}});
		statusLineManager.add(new StatusLineContributionItem("line", 10) {{
			setText("Line: \\n");
		}});

		statusLineManager.update(true);
		return statusLineManager;
	}

	private void createDelimiterMenu(StatusLineManager statusLineManager) {
		StatusLineMenuContribution fieldSeparatorMenu = new StatusLineMenuContribution("separator", "Delimiter: COMMA");
		statusLineManager.add(fieldSeparatorMenu);
		fieldSeparatorMenu.getMenuManager().add(new Action("COMMA") {
			@Override
			public void run() {
				CSVFormat newFormat = csv.getFormat().withDelimiter(',');
				fieldSeparatorMenu.setText("Delimiter: COMMA");
				updateFormat(newFormat);
			}
		});
		fieldSeparatorMenu.getMenuManager().add(new Action("TAB") {
			@Override
			public void run() {
				CSVFormat newFormat = csv.getFormat().withDelimiter('\t');
				fieldSeparatorMenu.setText("Delimiter: TAB");
				updateFormat(newFormat);
			}
		});
		fieldSeparatorMenu.getMenuManager().add(new Action("PIPE") {
			@Override
			public void run() {
				CSVFormat newFormat = csv.getFormat().withDelimiter('|');
				fieldSeparatorMenu.setText("Delimiter: PIPE");
				updateFormat(newFormat);
			}
		});
	}

	public void refreshTable() {
		viewer.setItemCount(csv.getRowCount());
	}

	private void updateFormat(CSVFormat newFormat) {
		csv.setFormat(newFormat);
		for (int i = viewer.getTable().getColumnCount() - 1; i > 0; i--) {
			viewer.getTable().getColumn(i).dispose();
		}
		viewer.refresh(true);
	}

}
