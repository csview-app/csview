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

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.AbstractUiBindingConfiguration;
import org.eclipse.nebula.widgets.nattable.config.DefaultNatTableStyleConfiguration;
import org.eclipse.nebula.widgets.nattable.config.IConfiguration;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.grid.data.DefaultCornerDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.data.DefaultRowHeaderDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.layer.ColumnHeaderLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.CornerLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.GridLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.RowHeaderLayer;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.layer.ILayer;
import org.eclipse.nebula.widgets.nattable.resize.action.ColumnResizeCursorAction;
import org.eclipse.nebula.widgets.nattable.resize.event.ColumnResizeEventMatcher;
import org.eclipse.nebula.widgets.nattable.resize.mode.ColumnResizeDragMode;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.selection.config.DefaultSelectionLayerConfiguration;
import org.eclipse.nebula.widgets.nattable.selection.config.DefaultSelectionStyleConfiguration;
import org.eclipse.nebula.widgets.nattable.style.HorizontalAlignmentEnum;
import org.eclipse.nebula.widgets.nattable.ui.action.ClearCursorAction;
import org.eclipse.nebula.widgets.nattable.ui.action.NoOpMouseAction;
import org.eclipse.nebula.widgets.nattable.ui.binding.UiBindingRegistry;
import org.eclipse.nebula.widgets.nattable.ui.matcher.MouseEventMatcher;
import org.eclipse.nebula.widgets.nattable.util.GUIHelper;
import org.eclipse.nebula.widgets.nattable.viewport.ViewportLayer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;


public class CSView extends ApplicationWindow {

	private CSV csv;
	private String file;
	private ProgressBar progressBar;
	private Composite progressRow;

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

	public CSView(File file) {
		super(null);
		
		csv = new CSV();
		
		try {
			loadCSV(file.toString());
		} catch (FileNotFoundException e) {
			loadCSVString(e.getMessage());
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
		
		Composite composite = (Composite) super.createContents(parent);
		
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		composite.setLayout(layout);
		
		// Body stack
		CSVDataProvider bodyDataProvider = new CSVDataProvider(csv);
		DataLayer bodyDataLayer = new DataLayer(bodyDataProvider);
		SelectionLayer selectionLayer = new SelectionLayer(bodyDataLayer, false);
		selectionLayer.addConfiguration(createSelectionLayerConfiguration());
		ViewportLayer viewportLayer = new ViewportLayer(selectionLayer);

		// Column header stack
		CSVHeaderProvider columnHeaderDataProvider = new CSVHeaderProvider(csv);
		DataLayer headerDataLayer = new DataLayer(columnHeaderDataProvider);
		ILayer columnHeaderLayer = new ColumnHeaderLayer(headerDataLayer,
				viewportLayer, 
				selectionLayer);

		// Create row header stack
		IDataProvider rowHeaderDataProvider = new DefaultRowHeaderDataProvider(bodyDataProvider);
		DataLayer rowHeaderDataLayer = new DataLayer(rowHeaderDataProvider, 40, DataLayer.DEFAULT_ROW_HEIGHT);
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

		NatTable natTable = new NatTable(composite, gridLayer, false);
		natTable.setLayoutData(new GridData(GridData.FILL_BOTH));
		natTable.addConfiguration(createTableConfiguration());
		natTable.addConfiguration(createUiBindingConfiguration());
		natTable.configure();
		GridDataFactory.fillDefaults().grab(true, true).applyTo(natTable);

		getShell().getDisplay().asyncExec(natTable::refresh);
		
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
					});
				}
				
				@Override
				public void changed(long progress) {
					getShell().getDisplay().asyncExec(() -> {
						progressBar.setSelection((int) ((progress*1000)/fileSize));
					});
				}
			});
		}

		// Load the CSV
		csv.scan();

		return composite;
	}

	private IConfiguration createUiBindingConfiguration() {
		return new AbstractUiBindingConfiguration() {
			@Override
			public void configureUiBindings(UiBindingRegistry uiBindingRegistry) {
				
				// TODO replace with less aggressive auto resize handler
				uiBindingRegistry.unregisterDoubleClickBinding(new ColumnResizeEventMatcher(SWT.NONE,
						GridRegion.COLUMN_HEADER, 1));

				uiBindingRegistry.registerMouseMoveBinding(new MouseEventMatcher(), new ClearCursorAction());
				
				for (String region: new String[] {GridRegion.ROW_HEADER, GridRegion.CORNER}) {
					// Mouse move - Show resize cursor
					uiBindingRegistry.registerFirstMouseMoveBinding(new ColumnResizeEventMatcher(SWT.NONE,
							region, 0), new ColumnResizeCursorAction());

					// Column resize
					uiBindingRegistry.registerFirstMouseDragMode(new ColumnResizeEventMatcher(SWT.NONE,
							region, 1), new ColumnResizeDragMode());
					uiBindingRegistry.registerSingleClickBinding(new ColumnResizeEventMatcher(SWT.NONE,
							region, 1), new NoOpMouseAction());
				}
			}
		};
	}

	private IConfiguration createTableConfiguration() {
		return new DefaultNatTableStyleConfiguration() {{
			hAlign = HorizontalAlignmentEnum.LEFT;
		}};
	}

	private IConfiguration createSelectionLayerConfiguration() {
		return new DefaultSelectionLayerConfiguration() {
			@Override
			protected void addSelectionStyleConfig() {
				addConfiguration(new DefaultSelectionStyleConfiguration() {{
					selectionFont = GUIHelper.DEFAULT_FONT;
				}});
			}
		};
	}
}
