package net.kothar.csview.grid;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GridTest {

	private Grid	grid;
	private Shell	shell;
	private Display	display;

	private HashMap<Point, String>	labels;
	private ImageData				cellData;

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

	private void testShell(Runnable... tests) {
		shell.open();
		grid.refresh();

		for (Runnable test : tests) {
			CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}
				display.asyncExec(test);
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

	@Test
	public void testScroll() {
		testShell(() -> {
			grid.setCols(100);
		}, () -> {
			grid.setCurrentCell(new Point(99, 0));
		}, () -> {
			assertTrue(grid.getXOffset() < grid.cols.getPosition(99));
			assertTrue(grid.getXOffset() + grid.getBounds().width >= grid.cols.getPosition(99) + grid.cols.getSize(99));
		});
	}
}
