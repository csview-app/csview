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

import static net.kothar.csview.ui.Adapters.*;

import java.io.File;
import java.io.FileNotFoundException;

import org.apache.commons.csv.CSVFormat;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.ibm.icu.text.CharsetDetector;

import net.kothar.csview.DocumentActions;
import net.kothar.csview.ProgressListener;
import net.kothar.csview.csv.CSV;
import net.kothar.csview.grid.Grid;
import net.kothar.csview.ui.search.SearchSidebar;

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
	private SashForm sashForm;
	private SearchSidebar sidebar;

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

		if (useAppIcon)
			shell.setImage(getAppIcon());

		if (file != null) {
			shell.setText(file + " - CSView");
		} else {
			shell.setText("CSView");
		}
	}

	@Override
	protected Point getInitialSize() {
		return new Point(1024, 768);
	}

	@Override
	protected Control createContents(Composite parent) {

		Composite composite = (Composite) super.createContents(parent);
		composite.setLayout(new FillLayout());

		sashForm = new SashForm(composite, SWT.HORIZONTAL);

		grid = new Grid(sashForm, SWT.BORDER);
		grid.setHeaderVisible(true);
		grid.setLinesVisible(true);

		grid.setContentProvider(new CSVContentProvider(csv));
		grid.setLabelProvider(new CSVLabelProvider(csv));
		grid.setRowLabelProvider(new NumberFormatLabelProvider(1));
		grid.setColumnLabelProvider(new CSVColumnHeaderProvider(csv));

		sidebar = new SearchSidebar(sashForm, csv);
		sashForm.setWeights(new int[] { 70, 30 });
		sashForm.setMaximizedControl(grid);
		sidebar.addCloseListener(select(this::hideSidebar));

		return composite;
	}

	@Override
	public void toggleSearch() {
		// TODO activate search control
		Control maximized = sashForm.getMaximizedControl();
		if (maximized == null) {
			hideSidebar();
		} else {
			showSidebar();
		}

	}

	public void showSidebar() {
		sashForm.setMaximizedControl(null);
		sidebar.focusInput();
	}

	public void hideSidebar() {
		sashForm.setMaximizedControl(grid);
	}

	@Override
	public void create() {
		addStatusLine();
		super.create();

		// Load the CSV
		csv.scan(createProgressListner("Scanning"));

		getShell().getDisplay().asyncExec(this::refreshTable);
		getShell().addDisposeListener(csv::dispose);
	}

	private ProgressListener createProgressListner(String task) {
		if (file != null) {
			IProgressMonitor progressMonitor = getStatusLineManager().getProgressMonitor();
			progressMonitor.beginTask(task, 1000);

			long fileSize = new File(file).length();
			ProgressListener listener = new ProgressListener() {

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
					if (getShell().isDisposed())
						return;

					getShell().getDisplay().asyncExec(() -> {
						int worked = ((int) (((progress - lastProgress) * 1000) / fileSize));
						if (worked > 0) {
							progressMonitor.worked(worked);
							lastProgress = progress;
						}
						refreshTable();
					});
				}

				@Override
				public void columnsChanged(int columns) {
					getShell().getDisplay().asyncExec(() -> grid.setCols(columns));
				}

			};
			
			return listener;
		}
		
		return new ProgressListener() {
			@Override
			public void completed() {
			}
			
			@Override
			public void columnsChanged(int columns) {
				getShell().getDisplay().asyncExec(() -> grid.setCols(columns));
			}
			
			@Override
			public void changed(long progress) {
			}
		};
	}

	protected StatusLineManager createStatusLineManager() {
		StatusLineManager statusLineManager = super.createStatusLineManager();

		createDelimiterMenu(statusLineManager);
		createEncodingMenu(statusLineManager);

		// statusLineManager.add(new StatusLineContributionItem("quote", 10) {{
		// setText("Escape: \"");
		// }});
		// statusLineManager.add(new StatusLineContributionItem("line", 10) {{
		// setText("Line: \\n");
		// }});

		statusLineManager.update(true);
		return statusLineManager;
	}

	private void createEncodingMenu(StatusLineManager statusLineManager) {
		StatusLineMenuContribution menu = new StatusLineMenuContribution("encoding",
				"Encoding: " + csv.getCharset() + " \u25bc");
		statusLineManager.add(menu);

		for (String encoding : CharsetDetector.getAllDetectableCharsets()) {
			menu.getMenuManager().add(new Action(encoding) {
				@Override
				public void run() {
					csv.setCharset(encoding);
					grid.refresh();
					menu.setText("Encoding: " + encoding + " \u25bc");
				}
			});
		}
	}

	private String formatDelimiter(char c) {
		for (Delimiter d : Delimiter.values()) {
			if (d.character == c)
				return "Delimiter: " + d + " \u25bc";
		}
		return "Delimiter: Custom - " + c + " \u25bc";
	}

	private void createDelimiterMenu(StatusLineManager statusLineManager) {

		char currentDelimiter = csv.getFormat().getDelimiter();
		StatusLineMenuContribution fieldSeparatorMenu = new StatusLineMenuContribution("separator",
				formatDelimiter(currentDelimiter));
		statusLineManager.add(fieldSeparatorMenu);

		for (Delimiter d : Delimiter.values()) {
			fieldSeparatorMenu.getMenuManager().add(new Action(d.toString()) {
				@Override
				public void run() {
					CSVFormat newFormat = csv.getFormat().withDelimiter(d.character);
					fieldSeparatorMenu.setText(formatDelimiter(d.character));
					updateFormat(newFormat);
				}
			});
		}
		fieldSeparatorMenu.getMenuManager().add(new Action("Custom...") {
			@Override
			public void run() {
				String defaultValue = "" + currentDelimiter;
				InputDialog inputDialog = new InputDialog(getShell(), "Select input delimiter",
						"Please choose a delimiter character", defaultValue, null);
				if (inputDialog.open() == Window.OK && !inputDialog.getValue().isEmpty()) {
					char delimiter = inputDialog.getValue().toCharArray()[0];
					CSVFormat newFormat = csv.getFormat().withDelimiter(delimiter);
					fieldSeparatorMenu.setText(formatDelimiter(delimiter));
					updateFormat(newFormat);
				}
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

	@Override
	public boolean copySelection() {
		grid.copySelection();
		return true;
	}

}
