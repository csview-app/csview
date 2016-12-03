package net.kothar.csview.grid;

import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ScrollBar;

public class Grid extends Composite {

	public static final int DEFAULT = -1;

	private static final int SCROLL_FACTOR = 4;
	
	private Canvas canvas;
	private ArrayList<Row> rows = new ArrayList<>();
	private ArrayList<Col> cols = new ArrayList<>();

	Grid(Composite parent, int style) {
		super(parent, style);

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
		
		// Hook mouseover
		canvas.addMouseMoveListener(this::mouseMove);
	}

	private void paintGrid(PaintEvent e) {
		GC gc = e.gc;
		Rectangle bounds = gc.getClipping();

		paintBackground(gc, bounds);
		paintGridlines(gc, bounds);
		paintSelection(gc, bounds);
		paintCells(gc, bounds);
		paintRowHeaders(gc, bounds);
		paintColHeaders(gc, bounds);
		paintCorner(gc);
	}

	private void paintCells(GC gc, Rectangle bounds) {
		// TODO Auto-generated method stub
		
	}

	private void paintSelection(GC gc, Rectangle bounds) {
		// TODO Auto-generated method stub
		
	}

	private void paintCorner(GC gc) {
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
		gc.fillRectangle(0, 0, getRowHeaderWidth(), getColumnHeaderHeight());
		gc.drawRectangle(0, 0, getRowHeaderWidth(), getColumnHeaderHeight());
	}

	private void paintRowHeaders(GC gc, Rectangle bounds) {
		int columnHeaderHeight = getColumnHeaderHeight();
		int rowHeaderWidth = getRowHeaderWidth();
		
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		gc.fillRectangle(0, columnHeaderHeight, rowHeaderWidth, canvas.getBounds().height - columnHeaderHeight);
		
		int yOffset = getYOffset();
		int yPos = getColumnHeaderHeight();
		int i = 0;

		Color textColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
		Color borderColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
		for (Row r: rows) {
			int y = yPos - yOffset;
			yPos += r.height;
			i++;

			if (y > bounds.height)
				break;
			if (y < bounds.y)
				continue;

			gc.setForeground(textColor);
			String text = Integer.toString(i);
			Point extent = gc.stringExtent(text);
			gc.drawString(Integer.toString(i), rowHeaderWidth - 5 - extent.x, y + 5);
			
			gc.setForeground(borderColor);
			gc.drawRectangle(0, y, rowHeaderWidth, r.height);
		}
	}

	private int getYOffset() {
		return canvas.getVerticalBar().getSelection() * SCROLL_FACTOR;
	}

	private void paintColHeaders(GC gc, Rectangle bounds) {
		int columnHeaderHeight = getColumnHeaderHeight();
		int rowHeaderWidth = getRowHeaderWidth();
		
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		gc.fillRectangle(rowHeaderWidth, 0, canvas.getBounds().width - rowHeaderWidth, columnHeaderHeight);
		
		int xOffset = getXOffset();
		int xPos = getRowHeaderWidth();
		int i = 0;

		Color textColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
		Color borderColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
		for (Col c: cols) {
			int x = xPos - xOffset;
			xPos += c.width;
			i++;

			if (x > bounds.width)
				break;
			if (x < bounds.x)
				continue;

			gc.setForeground(textColor);
			String text = Integer.toString(i);
			Point extent = gc.stringExtent(text);
			gc.drawString(text, x + (rowHeaderWidth - extent.x) / 2, 5);
			
			gc.setForeground(borderColor);
			gc.drawRectangle(x, 0, c.width, columnHeaderHeight);
		}
	}

	private int getXOffset() {
		return canvas.getHorizontalBar().getSelection() * SCROLL_FACTOR;
	}

	private void paintGridlines(GC gc, Rectangle bounds) {
		gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
		gc.setLineDash(new int[] {2,2});
		
		int yOffset = getYOffset();
		int yPos = getColumnHeaderHeight();
		for (Row r: rows) {
			int y = yPos + r.height - yOffset;
			yPos += r.height;
			
			if (y > bounds.height)
				break;
			if (y < bounds.y)
				continue;
			
			gc.drawLine(getRowHeaderWidth(), y, bounds.x + bounds.width, y);
		}
		
		int xOffset = getXOffset();
		int xPos = getRowHeaderWidth();
		for (Col c: cols) {
			int x = xPos + c.width - xOffset;
			xPos += c.width;
			
			if (x > bounds.width)
				break;
			if (x < bounds.x)
				continue;
			
			gc.drawLine(x, getColumnHeaderHeight(), x, bounds.y + bounds.height);
		}
		
		gc.setLineStyle(SWT.LINE_SOLID);
	}

	private void paintBackground(GC gc, Rectangle bounds) {
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
		gc.fillRectangle(bounds);
	}
	
	public void addRow(int height) {
		if (height == DEFAULT) {
			// TODO derive from font metrics
			height = 22;
		}
		rows.add(new Row(height));
		updateVerticalScroll();
	}

	private void updateVerticalScroll() {
		int max = getTotalHeight() - canvas.getBounds().height;
		ScrollBar bar = canvas.getVerticalBar();
		if (max > 0) {
			bar.setVisible(true);
			int total = (max + canvas.getHorizontalBar().getSize().y + getColumnHeaderHeight())/SCROLL_FACTOR + 11;
			bar.setMaximum(total);
		} else {
			bar.setVisible(false);
		}
	}

	private int getColumnHeaderHeight() {
		// TODO base on font metrics
		return 22;
	}

	private int getTotalHeight() {
		int height = 1;
		for (Row r: rows) {
			height += r.height;
		}
		return height;
	}
	
	public void addCol(int width) {
		if (width == DEFAULT) {
			// TODO derive from font metrics
			width = 100;
		}
		cols.add(new Col(width));
		updateHorizontalScroll();
	}

	private void updateHorizontalScroll() {
		int max = getTotalWidth() - canvas.getBounds().width;
		ScrollBar bar = canvas.getHorizontalBar();
		if (max > 0) {
			bar.setVisible(true);
			int total = (max + canvas.getVerticalBar().getSize().x + getRowHeaderWidth())/SCROLL_FACTOR + 11;
			bar.setMaximum(total);
		} else {
			bar.setVisible(false);
		}
	}

	private int getRowHeaderWidth() {
		// TODO base on font metrics
		return 100;
	}

	private int getTotalWidth() {
		int width = 0;
		for (Col c: cols) {
			width += c.width;
		}
		return width;
	}
	
	private void updateScroll() {
		updateHorizontalScroll();
		updateVerticalScroll();
	}
	
	private void mouseMove(MouseEvent e) {
		boolean rowHeaders = e.x <= getRowHeaderWidth();
		boolean colHeaders = e.y <= getColumnHeaderHeight();
		
		canvas.setCursor(null);
		
		if (rowHeaders && colHeaders) {
			cornerMouseMove(e);
		} else if (rowHeaders) {
			rowHeadersMouseMove(e);
		} else if (colHeaders) {
			colHeadersMouseMove(e);
		} else {
			cellsMouseMove(e);
		}
	}

	private void cellsMouseMove(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	private void colHeadersMouseMove(MouseEvent e) {
		canvas.setCursor(e.display.getSystemCursor(SWT.CURSOR_SIZEWE));
	}

	private void rowHeadersMouseMove(MouseEvent e) {
		canvas.setCursor(e.display.getSystemCursor(SWT.CURSOR_SIZENS));
	}

	private void cornerMouseMove(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

}
