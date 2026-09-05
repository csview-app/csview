package net.kothar.csview.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kothar.csview.ProgressListener;
import net.kothar.csview.csv.CSV;
import net.kothar.csview.csv.ProgressManager;
import net.kothar.csview.grid.Grid;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Checks the sizing against the providers CSView actually hands the grid, rather than against a
 * test double, so that a change in how cells are fetched still shows up as columns fitted to the
 * file that was opened.
 */
public class CSVAutoSizeTest {

    private static final String CONTENT = String.join("\n",
            "id,description,flag",
            "1,a description long enough to need a wide column,y",
            "2,another description of a similar length,n",
            "3,and a third one to measure,y");

    private Shell shell;
    private Display display;
    private Grid grid;
    private CSV csv;

    @Before
    public void openContent() throws Exception {
        shell = new Shell();
        display = shell.getDisplay();
        shell.setSize(800, 600);
        shell.setLayout(new FillLayout());

        StatusLineManager statusLine = new StatusLineManager();
        statusLine.createControl(shell);

        csv = new CSV();
        csv.setProgressManger(new ProgressManager(statusLine, display));
        csv.setContents(CONTENT);

        CompletableFuture<Void> scanned = new CompletableFuture<>();
        csv.scan(new ProgressListener() {
            @Override
            public void completed() {
                scanned.complete(null);
            }

            @Override
            public void changed() {
            }

            @Override
            public void columnsChanged(int columns) {
            }
        });
        scanned.get(10, TimeUnit.SECONDS);

        grid = new Grid(shell, SWT.NORMAL);
        grid.setContentProvider(new CSVContentProvider(csv));
        grid.setLabelProvider(new CSVLabelProvider(csv));
        grid.setRowLabelProvider(new NumberFormatLabelProvider(1));
        grid.setColumnLabelProvider(new CSVColumnHeaderProvider(csv));
    }

    @After
    public void cleanup() {
        shell.dispose();
        display.dispose();
    }

    @Test
    public void sizesTheColumnsOfAScannedFile() {
        grid.setRows(csv.getRowCount());
        grid.setCols(csv.getColCount());
        grid.refresh();
        grid.autoSizeColumns();

        assertEquals(3, csv.getColCount());
        assertTrue("The description column should be the widest",
                grid.getColumnSize(1) > grid.getColumnSize(0));
        assertTrue("The description column should be the widest",
                grid.getColumnSize(1) > grid.getColumnSize(2));
        assertTrue("Narrow columns should not be left at the default width",
                grid.getColumnSize(2) < grid.getRowHeaderSize());
    }
}
