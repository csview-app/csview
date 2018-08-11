package net.kothar.csview.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringEscapeUtils;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.HTMLTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
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
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.ScrollBar;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;

import net.kothar.csview.adt.SizeTree;

public class Grid extends Composite {

	public static final int	DEFAULT			= -1;
	static final int		SCROLL_FACTOR	= 6;
	static final int		TILE_ROWS		= 10;

	Canvas canvas;

	private int	horizontalCellPadding	= 5;
	private int	verticalCellPadding		= 2;

	SizeTree	rows	= new SizeTree(getColumnHeaderSize());
	SizeTree	cols	= new SizeTree(getRowHeaderSize());

	private IGridContentProvider	contentProvider;
	private ITableLabelProvider		labelProvider;
	ILabelProvider					rowLabelProvider;
	ILabelProvider					colLabelProvider;

	Selection selection = new Selection();

	private Integer	columnHeaderSize;
	private Integer	rowHeaderSize;

	Cache<Point, String> labelCache = CacheBuilder.newBuilder()
		.maximumSize(10_000)
		.build();

	Cache<Point, Image> tileCache = CacheBuilder.newBuilder()
		.maximumSize(1000)
		.removalListener((RemovalNotification<Point, Image> e) -> e.getValue().dispose())
		.build();

	private MouseHandler	mouseHandler;
	private Image			nullTile;
	private Point			currentCell;

	private List<CellListener>	currentCellListeners	= new ArrayList<>();
	private double				deviceZoom				= 1.0;
	private Transform			tileTransform;

	public Grid(Composite parent, int style) {
		super(parent, style);

		cols.setCount(1);
		rows.setCount(1);
		currentCell = new Point(0, 0);

		tileTransform = new Transform(parent.getDisplay());
		int maxZoom = Stream.of(Display.getCurrent().getMonitors()).mapToInt(Monitor::getZoom).max().orElse(100);
		if (maxZoom > 100) {
			deviceZoom = maxZoom / 100.0;
			tileTransform.scale((float) deviceZoom, (float) deviceZoom);
		}
		System.out.println("Render zoom factor: " + deviceZoom);

		setLayout(new FillLayout());
		createContents(this);
	}

	@Override
	public void dispose() {
		super.dispose();
		tileTransform.dispose();
	}

    private void createContents(Composite parent) {
        canvas = new Canvas(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.NO_BACKGROUND | SWT.NO_REDRAW_RESIZE);

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
		mouseHandler = createMouseHandler();

		KeyboardHandler keyListener = new KeyboardHandler(this);
		canvas.addKeyListener(keyListener);
	}

	protected MouseHandler createMouseHandler() {
		return new MouseHandler(this);
	}

	private void paintGrid(PaintEvent e) {
		GC gc = e.gc;

		if (rows.getTotal() > 0 && cols.getTotal() > 0)
			paintCells(gc);
		if (rows.getTotal() > 0)
			paintRowHeaders(gc);
		if (cols.getTotal() > 0)
			paintColHeaders(gc);
		paintCorner(gc);

		// Render border with bottom of control
		gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
		Rectangle bounds = getBounds();
		int y = bounds.height - 1;
		int width = bounds.width;
		gc.drawLine(0, y, width, y);
	}

	private void paintCells(GC gc) {

		int xOffset = getXOffset();
		int yOffset = getYOffset();

		int firstCol = xOffset < cols.getTotal() ? cols.getItemAt(xOffset) : cols.getCount() - 1;
		int firstX = cols.getPosition(firstCol);
		int firstRow = yOffset < rows.getTotal() ? rows.getItemAt(yOffset) : rows.getCount() - 1;
		firstRow -= firstRow % TILE_ROWS;

		int firstY = rows.getPosition(firstRow);
		int colCount = cols.getCount();
		int rowCount = rows.getCount();

		int xAdjust = xOffset - firstX;
		int yAdjust = yOffset - firstY;

		int columnHeaderSize = getColumnHeaderSize();
		int rowHeaderSize = getRowHeaderSize();

		int height = canvas.getBounds().height;
		int width = canvas.getBounds().width;
		Device device = gc.getDevice();
		getNullTile(device);

		int row = firstRow;
		for (int y = 0; row < rowCount && y + columnHeaderSize - yAdjust < height;) {

			int col = firstCol;
			int tileHeight = 0;
			for (int x = 0; col < colCount && x + rowHeaderSize - xAdjust < width;) {
				try {
					Point position = new Point(col, row);
					Image tile = tileCache.get(position, () -> renderTile(device, position));
					if (tileHeight == 0)
						tileHeight = (int) (tile.getBounds().height / deviceZoom);

					if (tile != nullTile) {
						int tileWidth = (int) (tile.getBounds().width / deviceZoom);
						gc.drawImage(tile,
							0, 0,
							tile.getBounds().width, tile.getBounds().height,
							x + rowHeaderSize - xAdjust, y + columnHeaderSize - yAdjust,
							tileWidth, tileHeight);
						x += tileWidth;
					}
					col++;
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			}

			y += tileHeight;
			row += TILE_ROWS;
		}
	}

	private void renderCells(GC gc, int y, Rectangle viewport) {
		if (contentProvider == null || labelProvider == null) {
			return;
		}

		int col = viewport.x;
		int row = viewport.y;

		Object element = contentProvider.getRow(row);

		try {
			String text = getLabel(col, row, element);
			if (text.isEmpty())
				return;

			if (selection.isSelected(col, row)) {
				gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT));
			} else {
				gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_LIST_FOREGROUND));
			}
			gc.drawString(text, getHorizontalCellPadding(), y + getVerticalCellPadding(), true);

		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

	private Image renderTile(Device device, Point position) {
		try {
			int width = cols.getSize(position.x);

			int lastY = rows.getTotal();
			if (position.y + TILE_ROWS < rows.getCount())
				lastY = rows.getPosition(position.y + TILE_ROWS);
			int height = lastY - rows.getPosition(position.y);

			if (width == 0 || height == 0)
				return nullTile;

			int zoomedWidth = (int) (width * deviceZoom);
			int zoomedHeight = (int) (height * deviceZoom);
			Image tile = new Image(device, zoomedWidth, zoomedHeight);
			GC gc = new GC(tile);
			gc.setTransform(tileTransform);

			renderBackground(gc, new Rectangle(0, 0, width, height));

			int row = position.y;
			int y = 0;
			for (int i = 0; i < TILE_ROWS && row < rows.getCount(); i++, row++) {
				int rowHeight = rows.getSize(row);
				Rectangle viewport = new Rectangle(position.x, row, width, rowHeight);

				renderSelection(gc, y, viewport);
				renderCells(gc, y, viewport);
				renderGridlines(gc, y, viewport);
				renderCurrentCell(gc, y, viewport);

				y += rowHeight;
			}

			gc.dispose();

			return tile;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private void renderCurrentCell(GC gc, int y, Rectangle viewport) {
		if (currentCell.x == viewport.x && currentCell.y == viewport.y) {
			gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
			gc.drawRectangle(0, y, viewport.width - 1, viewport.height - 1);
		}
	}

	private Image getNullTile(Device device) {
		if (nullTile == null) {
			nullTile = new Image(device, 1, 1);
		}
		return nullTile;
	}

	private void renderSelection(GC gc, int y, Rectangle viewport) {
		if (!selection.isSelected(viewport.x, viewport.y))
			return;

		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_LIST_SELECTION));
		gc.fillRectangle(0, y, viewport.width, viewport.height);
	}

	private void paintCorner(GC gc) {
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));

		int rowHeaderSize = getRowHeaderSize();
		int columnHeaderSize = getColumnHeaderSize();

		gc.fillRectangle(0, 0, rowHeaderSize, columnHeaderSize);
		gc.drawLine(0, columnHeaderSize - 1, rowHeaderSize - 1, columnHeaderSize - 1);
		gc.drawLine(rowHeaderSize - 1, 0, rowHeaderSize - 1, columnHeaderSize - 1);

		gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
		gc.drawLine(0, columnHeaderSize, rowHeaderSize - 1, columnHeaderSize);
		gc.drawLine(rowHeaderSize, 0, rowHeaderSize, columnHeaderSize - 1);
	}

	private void paintRowHeaders(GC gc) {
		Rectangle bounds = canvas.getBounds();

		int columnHeaderSize = getColumnHeaderSize();
		int rowHeaderSize = getRowHeaderSize();
		int horizontalCellPadding = getHorizontalCellPadding();
		int verticalCellPadding = getVerticalCellPadding();

		Device device = gc.getDevice();
		Color backgroundColor = device.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
		Color textColor = device.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
		Color borderColor = device.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
		Color borderColor2 = device.getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW);

		gc.setBackground(backgroundColor);
		gc.fillRectangle(0, columnHeaderSize, rowHeaderSize, canvas.getBounds().height - columnHeaderSize);

		int yOffset = getYOffset();

		gc.setClipping(0, 0, rowHeaderSize, bounds.height);

		int startRow;
		if (rows.getTotal() < yOffset) {
			startRow = rows.getCount() - 1;
		} else {
			startRow = rows.getItemAt(yOffset);
		}
		int y = columnHeaderSize + rows.getPosition(startRow) - yOffset;

		for (int i = startRow; i < rows.getCount(); i++) {
			int height = rows.getSize(i);

			if (y > bounds.height)
				break;
			if (y < bounds.y)
				continue;

			// Shadow header if row contains current cell
			if (currentCell != null && currentCell.y == i) {
				gc.setBackground(borderColor);
				gc.setForeground(borderColor2);

				gc.fillRectangle(0, y, rowHeaderSize, height);
			} else {
				gc.setBackground(backgroundColor);
				gc.setForeground(textColor);
			}

			// Render the label
			String text;
			if (rowLabelProvider != null) {
				text = rowLabelProvider.getText(i);
			} else {
				text = Integer.toString(i);
			}
			Point extent = gc.stringExtent(text);
			gc.drawString(text, rowHeaderSize - horizontalCellPadding - extent.x, y + verticalCellPadding);

			// Draw cell border
			gc.setForeground(borderColor);
			gc.drawLine(0, y - 1, rowHeaderSize - 1, y - 1);
			gc.setForeground(borderColor2);
			gc.drawLine(0, y, rowHeaderSize - 1, y);

			y += height;
		}

		// Right of row headers
		gc.setForeground(borderColor);
		gc.drawLine(rowHeaderSize - 1, columnHeaderSize, rowHeaderSize - 1, bounds.height);

		// Last row
		int bottomEdge = rows.getTotal() - yOffset + columnHeaderSize;
		if (bottomEdge < bounds.height) {
			gc.drawLine(0, bottomEdge, rowHeaderSize, bottomEdge);
		}

		// Highlight to left of row headers
		gc.setForeground(borderColor2);
		gc.drawLine(0, 0, 0, bounds.height);

		gc.setClipping((Rectangle) null);
	}

	public int getYOffset() {
		return canvas.getVerticalBar().getSelection() * SCROLL_FACTOR;
	}

	public void setYOffset(int yOffset) {
		canvas.getVerticalBar().setSelection(yOffset / SCROLL_FACTOR);
	}

	private void paintColHeaders(GC gc) {
		Rectangle bounds = canvas.getBounds();

		int columnHeaderSize = getColumnHeaderSize();
		int rowHeaderSize = getRowHeaderSize();
		int horizontalCellPadding = getHorizontalCellPadding();
		int verticalCellPadding = getVerticalCellPadding();

		Color backgroundColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
		Color textColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
		Color borderColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
		Color borderColor2 = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW);

		gc.setBackground(backgroundColor);
		gc.fillRectangle(rowHeaderSize, 0, canvas.getBounds().width - rowHeaderSize, columnHeaderSize);

		int xOffset = getXOffset();

		int startCol = xOffset < cols.getTotal() ? cols.getItemAt(xOffset) : cols.getCount();
		for (int i = startCol; i < cols.getCount(); i++) {
			int width = cols.getSize(i);
			int x = rowHeaderSize + cols.getPosition(i) - xOffset;

			if (x > bounds.width)
				break;
			if (x < bounds.x)
				continue;

			// Shadow selected cell's column header
			if (currentCell != null && currentCell.x == i) {
				gc.setBackground(borderColor);
				gc.setForeground(borderColor2);

				gc.fillRectangle(x, 0, width, columnHeaderSize);
			} else {
				gc.setBackground(backgroundColor);
				gc.setForeground(textColor);
			}

			// Render label
			String text;
			if (colLabelProvider != null) {
				text = colLabelProvider.getText(i);
			} else {
				text = Integer.toString(i);
			}

			gc.setClipping(x, 0, width, columnHeaderSize);
			gc.drawString(text, x + horizontalCellPadding, verticalCellPadding);
			gc.setClipping((Rectangle) null);

			// Render border between columns
			gc.setForeground(borderColor);
			gc.drawLine(x - 1, 0, x - 1, columnHeaderSize - 1);
			gc.setForeground(borderColor2);
			gc.drawLine(x, 0, x, columnHeaderSize - 1);
		}

		// Bottom of header
		gc.setForeground(borderColor);
		gc.drawLine(rowHeaderSize, columnHeaderSize - 1, bounds.width, columnHeaderSize - 1);

		// Last column
		int rightEdge = cols.getTotal() - xOffset + rowHeaderSize;
		if (rightEdge < bounds.width) {
			gc.drawLine(rightEdge, 0, rightEdge, columnHeaderSize);
		}

		// Top highlight
		gc.setForeground(borderColor2);
		gc.drawLine(0, 0, bounds.width, 0);
	}

	public int getXOffset() {
		return canvas.getHorizontalBar().getSelection() * SCROLL_FACTOR;
	}

	public void setXOffset(int xOffset) {
		canvas.getHorizontalBar().setSelection(xOffset / SCROLL_FACTOR);
	}

	public int getColAt(int x) {
		int cellX = x - getRowHeaderSize() + getXOffset();
		if (cellX < 0 || cellX >= cols.getTotal()) {
			return -1;
		}
		int colIndex = cols.getItemAt(cellX);
		return colIndex;
	}

	public int getRowAt(int y) {
		int cellY = y - getColumnHeaderSize() + getYOffset();
		if (cellY < 0 || cellY >= rows.getTotal()) {
			return -1;
		}
		int rowIndex = rows.getItemAt(cellY);
		return rowIndex;
	}

	private void renderGridlines(GC gc, int y, Rectangle viewport) {

		gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
		gc.setLineDash(new int[] { 2, 2 });

		gc.drawLine(0, y + viewport.height - 1, viewport.width - 1, y + viewport.height - 1);
		gc.drawLine(viewport.width - 1, y, viewport.width - 1, y + viewport.height - 1);

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
		canvas.redraw();
	}

	private void updateVerticalScroll() {
		if (isDisposed()) {
			return;
		}

		int max = getTotalHeight() - canvas.getBounds().height;
		ScrollBar bar = canvas.getVerticalBar();
		if (max > 0) {
			bar.setVisible(true);
			int total = (max + canvas.getHorizontalBar().getSize().y + getColumnHeaderSize()) / SCROLL_FACTOR + 11;
			bar.setMaximum(total);
			bar.setEnabled(true);
		} else {
			bar.setVisible(false);
			bar.setEnabled(false);
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
		canvas.redraw();
	}

	public void refresh() {
		updateScroll();
		labelCache.invalidateAll();
		redrawTiles();
	}

	private int getTotalHeight() {
		return rows.getTotal();
	}

	public int getRowCount() {
		return rows.getCount();
	}

	public int getColCount() {
		return cols.getCount();
	}

	public void addCol(int width) {
		if (width == DEFAULT) {
			// TODO derive from font metrics
			cols.add();
		} else {
			cols.add(width);
		}
		updateHorizontalScroll();
		canvas.redraw();
	}

	private void updateHorizontalScroll() {
		if (isDisposed()) {
			return;
		}

		int max = getTotalWidth() - canvas.getBounds().width;
		ScrollBar bar = canvas.getHorizontalBar();
		if (max > 0 || bar.getSelection() > 0) {
			bar.setVisible(true);
			int total = (max + canvas.getVerticalBar().getSize().x + getRowHeaderSize()) / SCROLL_FACTOR + 11;
			bar.setMaximum(total);
			bar.setEnabled(true);
		} else {
			bar.setVisible(false);
			bar.setEnabled(false);
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
		canvas.redraw();
	}

	public int getHorizontalCellPadding() {
		return horizontalCellPadding;
	}

	public void setHorizontalCellPadding(int padding) {
		this.horizontalCellPadding = padding;
		redrawTiles();
	}

	public int getVerticalCellPadding() {
		return verticalCellPadding;
	}

	public void setVerticalCellPadding(int padding) {
		this.verticalCellPadding = padding;
		redrawTiles();
	}

	void redrawTiles() {
		tileCache.invalidateAll();
		canvas.redraw();
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
		int oldCount = rows.getCount();
		rows.setCount(count);

		int minCount = Math.min(count, oldCount);
		int invalidateAfter = minCount - minCount % TILE_ROWS;
		if (invalidateAfter < count) {
			tileCache.asMap().keySet().removeIf(p -> p.y >= invalidateAfter);
			labelCache.asMap().keySet().removeIf(p -> p.y >= invalidateAfter);
		}
		updateVerticalScroll();

		canvas.redraw();
	}

	public void setHeaderVisible(boolean headerVisible) {
		// TODO Auto-generated method stub

	}

	public void setLinesVisible(boolean linesVisible) {
		// TODO Auto-generated method stub

	}

	public void setColumnSize(int column, int newSize) {
		cols.setSize(column, newSize);
		updateHorizontalScroll();

		tileCache.asMap().keySet().removeIf(p -> p.x == column);
		canvas.redraw();
	}

	private String getLabel(int col, int row, Object element) throws ExecutionException {
		Point point = new Point(col, row);
		String text = labelCache.get(point, () -> labelProvider.getColumnText(element, col));
		return text;
	}

	public String getLabel(int col, int row) {
		try {
			Object element = contentProvider.getRow(row);
			return getLabel(col, row, element);
		} catch (ExecutionException e) {
			e.printStackTrace();
			return "";
		}
	}

	public Selection getSelection() {
		return selection;
	}

	public void setRowLabelProvider(ILabelProvider rowLabelProvider) {
		this.rowLabelProvider = rowLabelProvider;
		redraw();
	}

	public void setColumnLabelProvider(ILabelProvider colLabelProvider) {
		this.colLabelProvider = colLabelProvider;
		redraw();
	}

	public Point getCurrentCell() {
		return currentCell;
	}

	public void setCurrentCell(Point cell) {
		if (!getCellBounds().contains(cell)) {
			return;
		}

		if (currentCell != null) {
			invalidateTiles(new Rectangle(currentCell.x, currentCell.y, 1, 1));
		}
		currentCell = cell;

		int x = cols.getPosition(cell.x);
		int y = rows.getPosition(cell.y);
		int width = cols.getSize(cell.x);
		int height = rows.getSize(cell.y);

		invalidateTiles(new Rectangle(cell.x, cell.y, 1, 1));

		// Adjust scroll position to ensure current cell is visible
		int xOffset = getXOffset();
		if (x + width / 2 < xOffset) {
			setXOffset(x);
		} else {
			int canvasWidth = canvas.getBounds().width;
			int rowHeaderSize = getRowHeaderSize();
			if (x + 16 > xOffset + canvasWidth - rowHeaderSize - 16) {
				setXOffset(x - canvasWidth + rowHeaderSize + width + 32);
			}
		}

		int yOffset = getYOffset();
		if (y < yOffset) {
			setYOffset(y);
		} else {
			int canvasHeight = canvas.getBounds().height;
			int columnHeaderSize = getColumnHeaderSize();
			if (y + height > yOffset + canvasHeight - columnHeaderSize - 16) {
				setYOffset(y - canvasHeight + columnHeaderSize + height + 32);
			}
		}

		currentCellListeners.forEach(l -> l.notify(currentCell));
	}

	public Rectangle getCellBounds() {
		return new Rectangle(0, 0, cols.getCount(), rows.getCount());
	}

	void invalidateTiles(Rectangle... regions) {
		invalidateTiles(Arrays.asList(regions));
	}

	void invalidateTiles(Iterable<Rectangle> regions) {
		tileCache.asMap().keySet().removeIf(p -> {
			Rectangle tileRegion = new Rectangle(p.x, p.y, 1, TILE_ROWS);
			for (Rectangle r : regions) {
				if (r.intersects(tileRegion))
					return true;
			}
			return false;
		});

		canvas.redraw();
	}

	public void copySelection() {
		// Get bounds of selection
		Rectangle bounds = selection.getUnion();
		if (bounds == null) {
			return;
		}

		String nl = System.getProperty("line.separator");

		// Prepare data
		HashMap<Integer, Integer> colSizes = new HashMap<>();
		for (int row = 0; row < bounds.height; row++) {
			for (int col = 0; col < bounds.width; col++) {
				if (selection.isSelected(col + bounds.x, row + bounds.y)) {
					String label = getLabel(col + bounds.x, row + bounds.y);
					if (label.isEmpty())
						continue;

					Integer size = colSizes.get(col);
					if (size == null)
						size = 0;
					colSizes.put(col, Math.max(size, label.length()));
				}
			}
		}

		StringBuilder text = new StringBuilder();
		StringBuilder html = new StringBuilder("<table style=\"border: 1px solid #999;\" cellspacing=\"0\">" + nl);

		for (int row = 0; row < bounds.height; row++) {
			html.append("<tr>" + nl);
			for (int col = 0; col < bounds.width; col++) {
				Integer size = colSizes.get(col);
				if (size == null || size == 0)
					continue;

				String label = "";
				if (selection.isSelected(col + bounds.x, row + bounds.y))
					label = getLabel(col + bounds.x, row + bounds.y);

				text.append(label);
				html.append("<td style=\"border: 1px dotted #ddd; padding: 3px 5px;\">"
					+ StringEscapeUtils.escapeHtml4(label) + "</td>" + nl);

				if (col < bounds.width - 1) {
					text.append(Strings.padStart(" ", size - label.length() + 3, ' '));
				}
			}
			text.append(nl);
			html.append("</tr>" + nl);
		}
		html.append("</table>" + nl);

		Object[] data = new Object[] { text.toString(), html.toString() };

		TextTransfer textTransfer = TextTransfer.getInstance();
		HTMLTransfer htmlTransfer = HTMLTransfer.getInstance();
		Transfer[] transfers = new Transfer[] { textTransfer, htmlTransfer };

		Clipboard clipboard = new Clipboard(getDisplay());
		clipboard.setContents(data, transfers);
		clipboard.dispose();
	}

	public void addCurrentCellListener(CellListener listener) {
		currentCellListeners.add(listener);
	}
}
