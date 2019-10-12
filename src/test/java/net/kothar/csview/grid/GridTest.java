package net.kothar.csview.grid;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.*;

public class GridTest {

    public static final String TEST_OUTPUT = "testOutput";

    private Grid grid;
    private Shell shell;
    private Display display;

    private HashMap<Point, String> labels;
    private ImageData cellData;

    @Before
    public void initGrid() {
        shell = new Shell();
        display = shell.getDisplay();

        shell.setSize(800, 600);
        shell.setLayout(new FillLayout());

        grid = new Grid(shell, SWT.NORMAL);

        grid.setContentProvider(new IGridContentProvider() {
            @Override
            public Object getRow(int index) {
                return index;
            }
        });

        labels = new HashMap<>();

        grid.setLabelProvider(new ITableLabelProvider() {

            @Override
            public String getColumnText(Object element, int column) {
                String label = labels.get(new Point(column, (int) element));
                if (label != null)
                    return label;
                return column + "," + element;
            }

            @Override
            public void removeListener(ILabelProviderListener arg0) {
            }

            @Override
            public boolean isLabelProperty(Object arg0, String arg1) {
                return false;
            }

            @Override
            public void dispose() {
            }

            @Override
            public void addListener(ILabelProviderListener arg0) {
            }

            @Override
            public Image getColumnImage(Object arg0, int arg1) {
                return null;
            }
        });

        grid.setRows(10);
        grid.setCols(5);
    }

    @After
    public void cleanup() {
        shell.dispose();
        display.dispose();
    }

    @Test
    public void testLabelCache() {

        assertEquals("2,5", grid.getLabel(2, 5));

        labels.put(new Point(2, 5), "Override");
        assertEquals("2,5", grid.getLabel(2, 5));

        grid.refresh();
        assertEquals("Override", grid.getLabel(2, 5));

    }

    @Test
    public void testInvalidate() {
        Point pos = new Point(2, 0);
        testShell(() -> {
            Image tile = grid.tileCache.getIfPresent(pos);
            assertNotNull(tile);
            assertFalse(tile.isDisposed());

            cellData = tile.getImageData();

            grid.invalidateTiles(new Rectangle(2, 2, 1, 1));
            assertTrue(tile.isDisposed());
            assertFalse(grid.tileCache.asMap().containsKey(pos));
        }, () -> {
            Image tile = grid.tileCache.getIfPresent(pos);
            assertNotNull(tile);
            assertFalse(tile.isDisposed());

            assertArrayEquals(tile.getImageData().data, cellData.data);

            // Change content of cell
            labels.put(pos, "Override");
            grid.refresh();
        }, () -> {
            Image tile = grid.tileCache.getIfPresent(pos);
            assertNotNull(tile);
            assertFalse(tile.isDisposed());

            try {
                assertArrayEquals(tile.getImageData().data, cellData.data);
                throw new RuntimeException("Image data should be different");
            } catch (AssertionError e) {
            }
        });
    }

    @Test
    public void testContentProvider() {
        Object row = grid.getContentProvider().getRow(5);
        assertEquals(5, row);
    }

    @Test
    public void testRender() {
        testShell(() -> {
            assertEquals(50, grid.labelCache.size());
            assertEquals(50 / grid.TILE_ROWS, grid.tileCache.size());
        });
    }

    private interface Step {
        void run() throws Exception;
    }

    private void testShell(Step... tests) {
        shell.open();
        grid.refresh();

        for (Step test : tests) {
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                }
                display.asyncExec(() -> {
                    // Drain any remaining paint events
                    while (display.readAndDispatch()) {
                    }

                    try {
                        test.run();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                });
            });

            while (!f.isDone()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }
        }

        while (display.readAndDispatch()) {
        }
        display.asyncExec(() -> shell.close());

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    @Test @Ignore
    public void testScrollToCell() {
        testShell(() -> {
            grid.setCols(100);
        }, () -> {
            grid.setCurrentCell(new Point(99, 0));
        }, () -> {
            assertTrue(grid.getXOffset() < grid.cols.getPosition(99));
            assertTrue(grid.getXOffset() + grid.getBounds().width >= grid.cols.getPosition(99) + grid.cols.getSize(99));
        });
    }

    @Test @Ignore
    public void testScroll() {
        ScrollBar horizontalBar = grid.canvas.getHorizontalBar();
        testShell(() -> {
            grid.setCols(100);
        }, () -> {
            scroll(horizontalBar, 1);
        }, () -> {
            assertEquals("Offset", 1 * Grid.SCROLL_FACTOR, grid.getXOffset());
            snapshot(grid, "after_scroll_1");
        }, () -> {
            scroll(horizontalBar, 30);
        }, () -> {
            assertEquals("Offset", 30 * Grid.SCROLL_FACTOR, grid.getXOffset());
            snapshot(grid, "after_scroll_2");
        }, () -> {
            scroll(horizontalBar, 20);
        }, () -> {
            assertEquals("Offset", 20 * Grid.SCROLL_FACTOR, grid.getXOffset());
            snapshot(grid, "after_scroll_3");
            grid.refresh();
        }, () -> {
            snapshot(grid, "after_scroll_3_repaint");
            assertSnapshotsMatch("after_scroll_3", "after_scroll_3_repaint");

            scroll(horizontalBar, 12);
            scroll(horizontalBar, 10);
        }, () -> {
            assertEquals("Offset", 10 * Grid.SCROLL_FACTOR, grid.getXOffset());
            snapshot(grid, "after_scroll_4");
            grid.refresh();
        }, () -> {
            snapshot(grid, "after_scroll_4_repaint");
            assertSnapshotsMatch("after_scroll_4", "after_scroll_4_repaint");
        });
    }

    private void assertSnapshotsMatch(String snap1, String snap2) {
        Image s1 = snapshots.get(snap1);
        Image s2 = snapshots.get(snap2);

        assertEquals(snap1 + " bounds", s1.getBounds(), s2.getBounds());
        assertArrayEquals(snap1 + " image data", s1.getImageData().data, s2.getImageData().data);
    }

    private void scroll(ScrollBar bar, int pos) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        bar.setSelection(pos);

        Method sendSelection = ScrollBar.class.getDeclaredMethod("sendSelection");
        sendSelection.setAccessible(true);
        sendSelection.invoke(bar);
    }

    private Map<String, Image> snapshots = new HashMap<>();

    private void snapshot(Grid grid, String name) {
        Display display = Display.getDefault();

        Image snapshot = new Image(display, grid.getBounds().width, grid.getBounds().height);
        GC gc = new GC(display);

        Point displayPos = grid.toDisplay(0, 0);
        gc.copyArea(snapshot, displayPos.x, displayPos.y);
        snapshots.put(name, snapshot);

        ImageLoader imageLoader = new ImageLoader();
        imageLoader.data = new ImageData[]{snapshot.getImageData()};
        new File(TEST_OUTPUT).mkdirs();
        imageLoader.save(TEST_OUTPUT + "/" + name + ".png", SWT.IMAGE_PNG);
    }
}
