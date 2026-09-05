package net.kothar.csview.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Opening a file should show its leading columns wide enough to read, rather than leaving every
 * column at the same default width and making the reader drag each one.
 */
public class GridAutoSizeTest {

    private static final String NARROW = "1";
    private static final String WIDE = "a value that needs a wider column";

    private Shell shell;
    private Display display;
    private Grid grid;

    private final Map<Point, String> labels = new HashMap<>();

    @Before
    public void initGrid() {
        shell = new Shell();
        display = shell.getDisplay();

        shell.setSize(800, 600);
        shell.setLayout(new FillLayout());

        grid = new Grid(shell, SWT.NORMAL);
        grid.setContentProvider(index -> index);
        grid.setLabelProvider(new ITableLabelProvider() {

            @Override
            public String getColumnText(Object element, int column) {
                String label = labels.get(new Point(column, (int) element));
                return label != null ? label : NARROW;
            }

            @Override
            public Image getColumnImage(Object element, int column) {
                return null;
            }

            @Override
            public boolean isLabelProperty(Object element, String property) {
                return false;
            }

            @Override
            public void addListener(ILabelProviderListener listener) {
            }

            @Override
            public void removeListener(ILabelProviderListener listener) {
            }

            @Override
            public void dispose() {
            }
        });

        grid.setRows(50);
        grid.setCols(Grid.AUTO_SIZE_MAX_COLS + 5);
    }

    @After
    public void cleanup() {
        shell.dispose();
        display.dispose();
    }

    @Test
    public void sizesLeadingColumnsToTheirContent() {
        labels.put(new Point(2, 3), WIDE);

        grid.autoSizeColumns();

        assertTrue("A column of wide content should be wider than one of narrow content",
                grid.getColumnSize(2) > grid.getColumnSize(1));
        assertTrue("A column of narrow content should not stay at the default width",
                grid.getColumnSize(1) < defaultColumnWidth());
    }

    @Test
    public void leavesColumnsPastTheLimitAlone() {
        labels.put(new Point(Grid.AUTO_SIZE_MAX_COLS, 3), WIDE);

        grid.autoSizeColumns();

        assertEquals(defaultColumnWidth(), grid.getColumnSize(Grid.AUTO_SIZE_MAX_COLS));
    }

    @Test
    public void onlySamplesTheRowsAtTheTop() {
        grid.setRows(Grid.AUTO_SIZE_MAX_ROWS * 2);
        labels.put(new Point(2, Grid.AUTO_SIZE_MAX_ROWS + 10), WIDE);

        grid.autoSizeColumns();

        assertEquals("Content below the sample should not widen the column",
                grid.getColumnSize(1), grid.getColumnSize(2));
    }

    @Test
    public void capsRunawayColumns() {
        labels.put(new Point(2, 3), WIDE.repeat(100));

        grid.autoSizeColumns();

        assertEquals(Grid.AUTO_SIZE_MAX_COL_WIDTH, grid.getColumnSize(2));
    }

    @Test
    public void keepsEmptyColumnsGrabbable() {
        for (int row = 0; row < 50; row++) {
            labels.put(new Point(2, row), "");
        }

        grid.autoSizeColumns();

        assertEquals(Grid.AUTO_SIZE_MIN_COL_WIDTH, grid.getColumnSize(2));
    }

    @Test
    public void doesNotMoveColumnsTheReaderHasSized() {
        grid.autoSizeColumns();

        grid.setColumnSize(1, 300);
        labels.put(new Point(1, 3), WIDE);

        // More of the file has arrived, so the remaining columns get measured again
        grid.setRows(Grid.AUTO_SIZE_MAX_ROWS);
        labels.put(new Point(2, 3), WIDE);
        grid.refresh();
        grid.autoSizeColumns();

        assertEquals(300, grid.getColumnSize(1));
        assertTrue("Columns the reader has not touched should still be sized to their content",
                grid.getColumnSize(2) > grid.getColumnSize(3));
    }

    @Test
    public void sizesAgainAfterTheColumnCountCollapsesAndReturns() {
        labels.put(new Point(2, 3), WIDE);
        grid.autoSizeColumns();

        // A scan announces the column count as it finds it, and can hand back a lower count than
        // the one already sized. The tree drops the widths of the columns that go and returns the
        // rest at the default, so what was measured before the drop no longer describes the grid.
        int columns = grid.getColCount();
        grid.setCols(0);
        grid.setCols(columns);
        grid.refresh();
        grid.autoSizeColumns();

        assertTrue("A column of wide content should be wider than one of narrow content",
                grid.getColumnSize(2) > grid.getColumnSize(1));
        assertTrue("A column of narrow content should not be left at the default width",
                grid.getColumnSize(1) < defaultColumnWidth());
    }

    @Test
    public void startsOverWhenTheColumnsChangeMeaning() {
        grid.setColumnSize(1, 300);
        labels.put(new Point(2, 3), WIDE);
        grid.autoSizeColumns();

        grid.resetColumnSizes();

        assertEquals(defaultColumnWidth(), grid.getColumnSize(1));
        assertEquals(defaultColumnWidth(), grid.getColumnSize(2));

        // The width the reader chose is forgotten along with the rest
        grid.refresh();
        grid.autoSizeColumns();
        assertTrue(grid.getColumnSize(1) < 300);
    }

    private int defaultColumnWidth() {
        return grid.getRowHeaderSize();
    }
}
