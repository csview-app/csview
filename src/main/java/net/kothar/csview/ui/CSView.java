/*
 * Copyright 2016 - 2018 Kothar Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.kothar.csview.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.DisposeEvent;
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
import net.kothar.csview.csv.ProgressManager;
import net.kothar.csview.grid.Grid;
import net.kothar.csview.ui.csv.CSVGrid;
import net.kothar.csview.ui.search.SearchSidebar;

public class CSView extends ApplicationWindow implements DocumentActions {

    public static final List<CSView> instances = new ArrayList<>();

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
    private CTabFolder sidebar;
    private String contents;

    private StatusLineMenuContribution rowCountStatus;
    private StatusLineMenuContribution colCountStatus;

    private SearchSidebar search;

    private CTabItem searchItem;

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
            file = args[0];
        } else {
            contents = "No Data, Please open a file";
        }
    }

    public CSView(File file) {
        this();

        this.file = file.toString();
    }

    public void load() {
        csv = new CSV();
        if (file != null) {
            try {
                loadCSV(file);
            } catch (FileNotFoundException e) {
                loadCSVString(e.getMessage());
            }
        } else {
            loadCSVString(contents);
        }
        instances.add(this);
    }

    public void useAppIcon() {
        useAppIcon = true;
    }

    private void loadCSVString(String string) {
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
            shell.setText(file);
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

        grid = new CSVGrid(sashForm, SWT.BORDER);
        grid.setHeaderVisible(true);
        grid.setLinesVisible(true);

        grid.setContentProvider(new CSVContentProvider(csv));
        grid.setLabelProvider(new CSVLabelProvider(csv));
        grid.setRowLabelProvider(new NumberFormatLabelProvider(1));
        grid.setColumnLabelProvider(new CSVColumnHeaderProvider(csv));

        sidebar = new CTabFolder(sashForm, SWT.CLOSE);
        sidebar.setSimple(false);
        sidebar.setUnselectedCloseVisible(true);
        sidebar.addCTabFolder2Listener(new CTabFolder2Adapter() {
            @Override
            public void close(CTabFolderEvent event) {
                if (sidebar.getItemCount() == 1) {
                    hideSidebar();
                }
            }
        });

        sashForm.setWeights(new int[]{70, 30});
        sashForm.setMaximizedControl(grid);

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
        if (search == null || search.isDisposed()) {
            search = new SearchSidebar(sidebar, csv);
            search.addSelectionChangedListener(this::handleSidebarSelection);
        }

        if (searchItem == null || searchItem.isDisposed()) {
            searchItem = new CTabItem(sidebar, SWT.CLOSE, 0);
            searchItem.setText("Search");
            searchItem.setControl(search);
        }

        sidebar.setSelection(0);
        sashForm.setMaximizedControl(null);
        search.focusInput();
    }

    public void hideSidebar() {
        sashForm.setMaximizedControl(grid);
    }

    public void handleSidebarSelection(SelectionChangedEvent e) {
        IStructuredSelection selection = (IStructuredSelection) e.getSelection();
        Point point = (Point) selection.getFirstElement();
        grid.setCurrentCell(point);
    }

    @Override
    public void gotoRow() {
        int rowCount = grid.getRowCount();
        InputDialog input = new InputDialog(getShell(), "Go to row", "Select a row number (1 to " + rowCount + ")",
                Integer.toString(grid.getCurrentCell().y + 1), v -> {
            try {
                int row = Integer.parseInt(v);
                if (row > 0 && row <= rowCount)
                    return null;
            } catch (Exception e) {
            }
            return "Not a valid row number";
        });
        input.setBlockOnOpen(true);
        if (input.open() == SWT.NORMAL) {
            int row = Integer.parseInt(input.getValue()) - 1;
            if (row < 0) {
                row = 0;
            } else if (row >= rowCount) {
                row = rowCount - 1;
            }
            Point currentCell = grid.getCurrentCell();
            grid.setCurrentCell(new Point(currentCell.x, row));
        }
    }

    @Override
    public void gotoCol() {
        int colCount = grid.getColCount();
        InputDialog input = new InputDialog(getShell(), "Go to column", "Select a column number (1 - " + colCount + ")",
                Integer.toString(grid.getCurrentCell().x + 1), v -> {
            try {
                int col = Integer.parseInt(v);
                if (col > 0 && col <= colCount)
                    return null;
            } catch (Exception e) {
            }
            return "Not a valid column number";
        });
        input.setBlockOnOpen(true);
        if (input.open() == SWT.NORMAL) {
            int col = Integer.parseInt(input.getValue()) - 1;
            if (col < 0) {
                col = 0;
            } else if (col >= colCount) {
                col = colCount - 1;
            }
            Point currentCell = grid.getCurrentCell();
            grid.setCurrentCell(new Point(col, currentCell.y));
        }
    }

    @Override
    public void create() {
        // Load the CSV
        load();

        addStatusLine();
        super.create();

        csv.setProgressManger(new ProgressManager(getStatusLineManager(), Display.getCurrent()));
        csv.scan(createProgressListener());

        getShell().getDisplay().asyncExec(this::refreshTable);
        getShell().addDisposeListener(this::dispose);
    }

    private void dispose(DisposeEvent e) {
        csv.dispose(e);
        instances.remove(this);
    }

    private ProgressListener createProgressListener() {
        if (file != null) {

            ProgressListener listener = new ProgressListener() {

                @Override
                public void completed() {
                    getShell().getDisplay().asyncExec(CSView.this::refreshTable);
                }

                @Override
                public void changed() {
                    if (getShell().isDisposed())
                        return;

                    getShell().getDisplay().asyncExec(CSView.this::refreshTable);
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
                getShell().getDisplay().asyncExec(CSView.this::refreshTable);
            }

            @Override
            public void columnsChanged(int columns) {
                getShell().getDisplay().asyncExec(() -> grid.setCols(columns));
            }

            @Override
            public void changed() {
                getShell().getDisplay().asyncExec(CSView.this::refreshTable);
            }
        };
    }

    @Override
    protected StatusLineManager createStatusLineManager() {
        StatusLineManager statusLineManager = super.createStatusLineManager();

        createLineCountStatus(statusLineManager);

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

    private void createLineCountStatus(StatusLineManager statusLineManager) {
        rowCountStatus = new StatusLineMenuContribution("lineCount", "Rows: 0");
        rowCountStatus.getMenuManager().addMenuListener(m -> gotoRow());
        statusLineManager.add(rowCountStatus);
        colCountStatus = new StatusLineMenuContribution("lineCount", "Columns: 0");
        colCountStatus.getMenuManager().addMenuListener(m -> gotoCol());
        statusLineManager.add(colCountStatus);
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
                    CSVFormat newFormat = null;
                    char delimiter = inputDialog.getValue().toCharArray()[0];

                    try {
                        newFormat = csv.getFormat().withDelimiter(delimiter);
                    } catch (IllegalArgumentException e) {
                        ErrorDialog.openError(getShell(), "Input error", "Unable to set delimiter",
                                new Status(IStatus.WARNING, "CSView", e.getMessage(), e));
                        return;
                    }

                    fieldSeparatorMenu.setText(formatDelimiter(delimiter));
                    updateFormat(newFormat);
                }
            }
        });
    }

    public void refreshTable() {
        grid.setRows(csv.getRowCount());
        rowCountStatus.setText(String.format("Rows: %,d", csv.getRowCount()));
        colCountStatus.setText(String.format("Columns: %,d", csv.getColCount()));
    }

    private void updateFormat(CSVFormat newFormat) {
        grid.setCols(1);
        csv.setFormat(newFormat);
        csv.scan(createProgressListener());
    }

    @Override
    public boolean copySelection() {
        grid.copySelection();
        return true;
    }

}
