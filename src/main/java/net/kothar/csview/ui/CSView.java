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
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import net.kothar.csview.DocumentActions;
import net.kothar.csview.ProgressListener;
import net.kothar.csview.csv.CSV;
import net.kothar.csview.grid.Grid;


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

	private Grid grid;

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

		grid = new Grid(composite, SWT.NORMAL);
		grid.setLayoutData(new GridData(GridData.FILL_BOTH));
		grid.setHeaderVisible(true);
		grid.setLinesVisible(true);

		grid.setContentProvider(new CSVContentProvider(csv));
		grid.setLabelProvider(new CSVLabelProvider());

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
					grid.setCols(columns);
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
		grid.setRows(csv.getRowCount());
	}

	private void updateFormat(CSVFormat newFormat) {
		grid.setCols(1);
		csv.setFormat(newFormat);
	}

}
