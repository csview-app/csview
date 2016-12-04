package net.kothar.csview.grid;

import java.util.concurrent.ExecutionException;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ScrollBar;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;

import net.kothar.csview.adt.SizeTree;

public class Grid extends Composite {

	public static final int DEFAULT = -1;

	private static final int SCROLL_FACTOR = 4;

	private static final int TILE_SIZE = 256;

	Canvas canvas;

	private int horizontalCellPadding = 5;
	private int verticalCellPadding = 2;

	private SizeTree rows = new SizeTree(getColumnHeaderSize());
	private SizeTree cols = new SizeTree(getRowHeaderSize());

	private IGridContentProvider contentProvider;
	private ITableLabelProvider labelProvider;

	private Integer columnHeaderSize;
	private Integer rowHeaderSize;

	private Cache<Point, String> labelCache = 
			CacheBuilder.newBuilder()
			.maximumSize(10_000)
			.build();

	private Cache<Point, Image> tileCache = 
			CacheBuilder.newBuilder()
			.maximumSize(1000)
			.removalListener((RemovalNotification<Point, Image> e) -> e.getValue().dispose())
			.build();

	private MouseHandler mouseHandler;

	public Grid(Composite parent, int style) {
		super(parent, style);

		cols.setCount(1);
		rows.setCount(1);
		
		setLayout(new FillLayout());
		createContents(this);
	}

	private void createContents(Composite parent) {
		canvas = new Canvas(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.DOUBLE_BUFFERED);

		// Hook painting
		canvas.addPaintListener(this::paintGrid);

		// Hook resize
		canvas.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				updateScroll();
			}
		});

		// Hook scrolling
		SelectionAdapter redraw = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				canvas.redraw();
			}
		};
		canvas.getVerticalBar().addSelectionListener(redraw);
		canvas.getHorizontalBar().addSelectionListener(redraw);

		// Hook mouse handling
		mouseHandler = new MouseHandler(this);
	}

	private void paintGrid(PaintEvent e) {
		long start = System.currentTimeMillis();

		// Determine if there's an overlap between new and old display
		GC gc = e.gc;

		paintCells(gc);
		paintRowHeaders(gc);
		paintColHeaders(gc);
		paintCorner(gc);

		long elapsed = System.currentTimeMillis() - start;
		if (elapsed > 10)
			System.out.println("Rendered in " + elapsed + "ms");
	}



	private void paintCells(GC gc) {
		int xOffset = getXOffset();
		int yOffset = getYOffset();

		int xAdjust = xOffset % TILE_SIZE;
		int yAdjust = yOffset % TILE_SIZE;

		for (int y = 0; y < canvas.getBounds().height - getColumnHeaderSize() + TILE_SIZE; y += TILE_SIZE) {
			for (int x = 0; x < canvas.getBounds().width - getRowHeaderSize() + TILE_SIZE; x += TILE_SIZE) {
				try {
					Point position = new Point(x + xOffset - xAdjust, y + yOffset - yAdjust);
					Image tile = tileCache.get(position, () -> renderTile(gc.getDevice(), position.x, position.y));

					gc.drawImage(tile, x + getRowHeaderSize() - xAdjust, y + getColumnHeaderSize() - yAdjust);
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private void renderCells(GC gc, Rectangle viewport) {

		if (contentProvider == null || labelProvider == null) {
			return;
		}

		int horizontalCellPadding = getHorizontalCellPadding();
		int verticalCellPadding = getVerticalCellPadding();

		int yOffset = viewport.y;
		int xOffset = viewport.x;

		Color textColor = gc.getDevice().getSystemColor(SWT.COLOR_LIST_FOREGROUND);
		gc.setForeground(textColor);

		for (int row = rows.getItemAt(yOffset); row < rows.getCount(); row++) {
			int height = rows.getSize(row);
			int y = rows.getPosition(row) - yOffset;

			if (y > viewport.height)
				break;

			Object element = contentProvider.getRow(row);

			for (int col = cols.getItemAt(xOffset); col < cols.getCount(); col++) {
				int width = cols.getSize(col);
				int x = cols.getPosition(col) - xOffset;

				if (x > viewport.width)
					break;

				try {
					Point point = new Point(col, row);
					String text = labelCache.get(point, () -> labelProvider.getColumnText(element, point.x));
					if (text.isEmpty())
						continue;

					gc.setClipping(x, y, width, height);
					gc.drawString(text, x + horizontalCellPadding, y + verticalCellPadding, false);

				} catch (ExecutionException e) {
					e.printStackTrace();
					continue;
				}
			}
		}
		
		gc.setClipping((Rectangle) null);
	}

	private Image renderTile(Device device, int xOffset, int yOffset) {
		long start = System.currentTimeMillis();

		try {
			Image tile = new Image(device, TILE_SIZE, TILE_SIZE);
			GC gc = new GC(tile);
			Rectangle viewport = new Rectangle(xOffset, yOffset, TILE_SIZE, TILE_SIZE);
			renderBackground(gc, viewport);

			if (viewport.x < cols.getTotal() && viewport.y < rows.getTotal()) {
				renderSelection(gc, viewport);
				renderCells(gc, viewport);
				renderGridlines(gc, viewport);
			}

			gc.dispose();

			long elapsed = System.currentTimeMillis() - start;
			if (elapsed > 10)
				System.out.println("Rendered tile (" + xOffset + "," + yOffset + ") in " + elapsed + "ms");
			return tile;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private void renderSelection(GC gc, Rectangle viewport) {
		// TODO Auto-generated method stub

	}

	private void paintCorner(GC gc) {
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
		
		int rowHeaderSize = getRowHeaderSize();
		int columnHeaderSize = getColumnHeaderSize();
		
		gc.fillRectangle(0, 0, rowHeaderSize, columnHeaderSize);
		gc.drawLine(0, columnHeaderSize, rowHeaderSize, columnHeaderSize);
		gc.drawLine(rowHeaderSize, 0, rowHeaderSize, columnHeaderSize);
		
		gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
		gc.drawLine(0, columnHeaderSize+1, rowHeaderSize, columnHeaderSize+1);
		gc.drawLine(rowHeaderSize+1, 0, rowHeaderSize+1, columnHeaderSize);
	}

	private void paintRowHeaders(GC gc) {
		Rectangle bounds = canvas.getBounds();

		int columnHeaderSize = getColumnHeaderSize();
		int rowHeaderSize = getRowHeaderSize();
		int horizontalCellPadding = getHorizontalCellPadding();
		int verticalCellPadding = getVerticalCellPadding();

		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		gc.fillRectangle(0, columnHeaderSize, rowHeaderSize, canvas.getBounds().height - columnHeaderSize);

		int yOffset = getYOffset();

		Color textColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
		Color borderColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
		Color borderColor2 = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW);

		for (int i = rows.getItemAt(yOffset); i < rows.getCount(); i++) {
			int height = rows.getSize(i);
			int y = columnHeaderSize + rows.getPosition(i) - yOffset;

			if (y > bounds.height)
				break;
			if (y < bounds.y)
				continue;

			gc.setForeground(textColor);
			String text = Integer.toString(i);
			Point extent = gc.stringExtent(text);
			
			gc.setClipping(0, y, rowHeaderSize, height);
			gc.drawString(Integer.toString(i), rowHeaderSize - horizontalCellPadding - extent.x, y + verticalCellPadding);
			gc.setClipping((Rectangle) null);

			gc.setForeground(borderColor);
			gc.drawLine(0, y, rowHeaderSize, y);
			gc.setForeground(borderColor2);
			gc.drawLine(0, y+1, rowHeaderSize, y+1);
		}

		gc.setForeground(borderColor);
		gc.drawLine(rowHeaderSize, columnHeaderSize, rowHeaderSize, bounds.height);
		gc.setForeground(borderColor2);
		gc.drawLine(0, 0, 0, bounds.height);
	}

	private int getYOffset() {
		return canvas.getVerticalBar().getSelection() * SCROLL_FACTOR;
	}

	private void paintColHeaders(GC gc) {
		Rectangle bounds = canvas.getBounds();

		int columnHeaderSize = getColumnHeaderSize();
		int rowHeaderSize = getRowHeaderSize();
		int horizontalCellPadding = getHorizontalCellPadding();
		int verticalCellPadding = getVerticalCellPadding();

		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		gc.fillRectangle(rowHeaderSize, 0, canvas.getBounds().width - rowHeaderSize, columnHeaderSize);

		int xOffset = getXOffset();

		Color textColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
		Color borderColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
		Color borderColor2 = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW);
		for (int i = cols.getItemAt(xOffset); i < cols.getCount(); i++) {
			int width = cols.getSize(i);
			int x = rowHeaderSize + cols.getPosition(i) - xOffset;

			if (x > bounds.width)
				break;
			if (x < bounds.x)
				continue;

			gc.setForeground(textColor);
			String text = Integer.toString(i);
			Point extent = gc.stringExtent(text);
			
			gc.setClipping(x, 0, width, rowHeaderSize);
			gc.drawString(text, x + (rowHeaderSize - extent.x) / 2, verticalCellPadding);
			gc.setClipping((Rectangle) null);
			
			gc.setForeground(borderColor);
			gc.drawLine(x, 0, x, columnHeaderSize);
			gc.setForeground(borderColor2);
			gc.drawLine(x+1, 0, x+1, columnHeaderSize);
		}

		gc.setForeground(borderColor);
		gc.drawLine(rowHeaderSize, columnHeaderSize, bounds.width, columnHeaderSize);
		gc.setForeground(borderColor2);
		gc.drawLine(0, 0, bounds.width, 0);
	}

	private int getXOffset() {
		return canvas.getHorizontalBar().getSelection() * SCROLL_FACTOR;
	}

	private void renderGridlines(GC gc, Rectangle viewport) {

		gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
		gc.setLineDash(new int[] {2,2});

		int yOffset = viewport.y;
		for (int i = rows.getItemAt(yOffset); i < rows.getCount(); i++) {
			int y = rows.getPosition(i) - yOffset;

			if (y > viewport.height)
				break;
			if (y < 0)
				continue;

			gc.drawLine(0, y, viewport.width, y);
		}

		int xOffset = viewport.x;
		for (int i = cols.getItemAt(xOffset); i < cols.getCount(); i++) {
			int x = cols.getPosition(i) - xOffset;

			if (x > viewport.width)
				break;
			if (x < 0)
				continue;

			gc.drawLine(x, 0, x, viewport.height);
		}

		gc.setLineStyle(SWT.LINE_SOLID);
	}

	private void renderBackground(GC gc, Rectangle viewport) {
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
		gc.fillRectangle(0, 0, viewport.width, viewport.height);
	}

	public void addRow(int height) {
		if (height == DEFAULT) {
			// TODO derive from font metrics
			rows.add();
		} else {
			rows.add(height);
		}
		updateVerticalScroll();
	}

	private void updateVerticalScroll() {
		int max = getTotalHeight() - canvas.getBounds().height;
		ScrollBar bar = canvas.getVerticalBar();
		if (max > 0) {
			bar.setVisible(true);
			int total = (max + canvas.getHorizontalBar().getSize().y + getColumnHeaderSize())/SCROLL_FACTOR + 11;
			bar.setMaximum(total);
		} else {
			bar.setVisible(false);
		}
	}

	public int getColumnHeaderSize() {
		if (columnHeaderSize == null) {
			GC gc = new GC(Display.getDefault());
			columnHeaderSize = gc.getFontMetrics().getHeight() + 2 * verticalCellPadding;
			gc.dispose();
		}
		return columnHeaderSize;
	}

	public void setColumnHeaderSize(int size) {
		this.columnHeaderSize = size;
		refresh();
	}

	private void refresh() {
		updateScroll();
		labelCache.invalidateAll();
		tileCache.invalidateAll();
		canvas.redraw();
	}

	private int getTotalHeight() {
		return rows.getTotal();
	}

	public void addCol(int width) {
		if (width == DEFAULT) {
			// TODO derive from font metrics
			cols.add();
		} else {
			cols.add(width);
		}
		updateHorizontalScroll();
	}

	private void updateHorizontalScroll() {
		int max = getTotalWidth() - canvas.getBounds().width;
		ScrollBar bar = canvas.getHorizontalBar();
		if (max > 0) {
			bar.setVisible(true);
			int total = (max + canvas.getVerticalBar().getSize().x + getRowHeaderSize())/SCROLL_FACTOR + 11;
			bar.setMaximum(total);
		} else {
			bar.setVisible(false);
		}
	}

	public int getRowHeaderSize() {
		if (rowHeaderSize == null) {
			GC gc = new GC(Display.getDefault());
			rowHeaderSize = gc.getFontMetrics().getAverageCharWidth() * 10 + 2 * getHorizontalCellPadding();
			gc.dispose();
		}
		return rowHeaderSize;
	}

	public void setRowHeaderSize(int size) {
		this.rowHeaderSize = size;
	}

	public int getHorizontalCellPadding() {
		return horizontalCellPadding;
	}

	public void setHorizontalCellPadding(int padding) {
		this.horizontalCellPadding = padding;
		refresh();
	}

	public int getVerticalCellPadding() {
		return verticalCellPadding;
	}

	public void setVerticalCellPadding(int padding) {
		this.verticalCellPadding = padding;
		refresh();
	}

	private int getTotalWidth() {
		return cols.getTotal();
	}

	private void updateScroll() {
		updateHorizontalScroll();
		updateVerticalScroll();
	}

	public IGridContentProvider getContentProvider() {
		return contentProvider;
	}

	public void setContentProvider(IGridContentProvider contentProvider) {
		this.contentProvider = contentProvider;
	}

	public ITableLabelProvider getLabelProvider() {
		return labelProvider;
	}

	public void setLabelProvider(ITableLabelProvider labelProvider) {
		this.labelProvider = labelProvider;
	}

	public void setCols(int count) {
		cols.setCount(count);
		refresh();
	}

	public void setRows(int count) {
		rows.setCount(count);
		refresh();
	}

	public void setHeaderVisible(boolean headerVisible) {
		// TODO Auto-generated method stub
		
	}

	public void setLinesVisible(boolean linesVisible) {
		// TODO Auto-generated method stub
		
	}

}
