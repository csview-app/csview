package net.kothar.csview.grid;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import net.kothar.csview.Timer;
import net.kothar.csview.adt.SizeTree;
import org.apache.commons.lang3.StringEscapeUtils;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.HTMLTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ScrollBar;
import org.freedesktop.Platform;
import org.freedesktop.platforms.Windows;

public class Grid extends Composite {

  public static final int DEFAULT = -1;
  static final int SCROLL_FACTOR = 6;
  static final int TILE_ROWS = 10;

  Canvas canvas;
  Theme theme = new DefaultTheme(this);

  private int horizontalCellPadding = 5;
  private int verticalCellPadding = 2;

  SizeTree rows = new SizeTree(getColumnHeaderSize());
  SizeTree cols = new SizeTree(getRowHeaderSize());

  private Rectangle cleanRegion = null;

  private IGridContentProvider contentProvider;
  private ITableLabelProvider labelProvider;
  ILabelProvider rowLabelProvider;
  ILabelProvider colLabelProvider;

  Selection selection = new Selection();

  private Integer columnHeaderSize;
  private Integer rowHeaderSize;

  Cache<Point, String> labelCache = CacheBuilder.newBuilder()
      .maximumSize(10_000)
      .build();

  Cache<Point, Image> tileCache = CacheBuilder.newBuilder()
      .maximumSize(1000)
      .removalListener((RemovalNotification<Point, Image> e) -> {
        if (e.getValue() != null) {
          e.getValue().dispose();
        }
      })
      .build();

  private MouseHandler mouseHandler;
  private Image nullTile;
  private Point currentCell;

  private List<CellListener> currentCellListeners = new ArrayList<>();
  private double deviceZoom = 1.0;
  private Transform tileTransform;

  private Point origin = new Point(0, 0);
  private AtomicBoolean scrolling = new AtomicBoolean(false);

  public Grid(Composite parent, int style) {
    super(parent, style);

    cols.setCount(1);
    rows.setCount(1);
    currentCell = new Point(0, 0);

    tileTransform = new Transform(parent.getDisplay());
    int maxZoom = DPIUtil.autoScaleUp(100);
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
    int style = SWT.H_SCROLL | SWT.V_SCROLL | SWT.NO_BACKGROUND;
    if (Platform.getCurrent().getClass() == Windows.class) {
      style |= SWT.DOUBLE_BUFFERED;
    }
    canvas = new Canvas(parent, style);

    // Hook painting
    canvas.addPaintListener(this::paintGrid);

    // Hook scrolling
    SelectionAdapter onScroll = new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        scrollCanvas();
      }
    };
    canvas.getHorizontalBar().addSelectionListener(onScroll);
    canvas.getVerticalBar().addSelectionListener(onScroll);
    canvas.addControlListener(new ControlAdapter() {
      @Override
      public void controlResized(ControlEvent e) {
        updateScroll();
      }
    });

    // Hook mouse handling
    mouseHandler = createMouseHandler();

    KeyboardHandler keyListener = new KeyboardHandler(this);
    canvas.addKeyListener(keyListener);
  }

  private void scrollCanvas() {
    if (scrolling.getAndSet(true)) {
      getDisplay().asyncExec(this::scrollCanvas);
      return;
    }

    int newX = canvas.getHorizontalBar().getSelection() * SCROLL_FACTOR;
    int oldX = origin.x;
    int shiftX = newX - oldX;

    int newY = canvas.getVerticalBar().getSelection() * SCROLL_FACTOR;
    int oldY = origin.y;
    int shiftY = newY - oldY;

    if (shiftX == 0 && shiftY == 0) {
      scrolling.set(false);
      return;
    }

    int rowHeader = getRowHeaderSize();
    int colHeader = getColumnHeaderSize();
    int canvasWidth = canvas.getBounds().width;
    int canvasHeight = canvas.getBounds().height;

    origin.x = newX;
    origin.y = newY;

    if (Math.abs(shiftX) > canvasWidth - rowHeader
        || Math.abs(shiftY) > canvasHeight - colHeader
        || (shiftX != 0 && shiftY != 0)) {
      cleanRegion = null;
      canvas.redraw();
    } else {
      int destX = 0;
      int x = 0;
      int width = canvasWidth;
      if (shiftX < 0) {
        // Shift pixels right
        destX = -shiftX + rowHeader;
        x = rowHeader;
        width = canvasWidth - shiftX - rowHeader;
      } else if (shiftX > 0) {
        // Shift pixels left
        destX = rowHeader;
        x = shiftX + rowHeader;
        width = canvasWidth - shiftX - rowHeader;
      }

      int height = canvasHeight;
      int y = 0;
      int destY = 0;
      if (shiftY < 0) {
        // Shift pixels down
        destY = -shiftY + colHeader;
        y = colHeader;
        height = canvasHeight - shiftY - colHeader;
      } else if (shiftY > 0) {
        // Shift pixels up
        destY = colHeader;
        y = shiftY + colHeader;
        height = canvasHeight - shiftY - colHeader;
      }

      cleanRegion = new Rectangle(destX, destY, width, height);
      if (shiftY < 0) {
        cleanRegion.height += canvasHeight;
      } else if (shiftY > 0) {
        cleanRegion.y -= canvasHeight;
        cleanRegion.height += canvasHeight;
      }
//      canvas.scroll(destX, destY, x, y, width, height, false);
      canvas.redraw();
    }
  }

  protected MouseHandler createMouseHandler() {
    return new MouseHandler(this);
  }

  private void paintGrid(PaintEvent e) {
    boolean debug = Boolean.getBoolean("csview.debug.paint");
    Timer timer = debug ? new Timer("Paint") : null;

    GC gc = e.gc;

    if (rows.getTotal() > 0 && cols.getTotal() > 0) {
      paintCells(e, timer);
    }
    if (rows.getTotal() > 0) {
      if (timer != null) {
        timer.subTask("Row Headers");
      }
      paintRowHeaders(e);
    }
    if (cols.getTotal() > 0) {
      if (timer != null) {
        timer.subTask("Col Headers");
      }
      paintColHeaders(e);
    }

    if (timer != null) {
      timer.subTask("Chrome");
    }
    paintCorner(e);
    paintBackground(e);

    // Render border with bottom of control
    Point size = getSize();
    gc.setForeground(theme.getOuterBorderColor());
    int y = size.y - 1;
    gc.drawLine(0, y, size.x, y);

    scrolling.set(false);

    if (debug) {

      gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_RED));
      gc.drawRectangle(gc.getClipping().x, gc.getClipping().y, gc.getClipping().width - 1,
          gc.getClipping().height - 1);

      long fps = Duration.ofSeconds(1).toNanos() / timer.total().toNanos();
      if (fps < 30) {
        System.out.println("Paint at  " + origin);
        System.err.println("Repaint " + e.gc.getClipping().width * e.gc.getClipping().height +
            " pixels in " + timer.total().toMillis() + "ms (" + fps + " fps)");
        timer.tasks.forEach((task, duration) -> {
          System.err.printf("%s: %dms\n", task, Duration.ofNanos(duration).toMillis());
        });
      }
    }
  }

  private void paintBackground(PaintEvent e) {
    GC gc = e.gc;
    Point size = getSize();
    int width = size.x;
    int height = size.y;

    // Paint background for region outside cells
    int colsEnd = cols.getTotal() - getXOffset() + rowHeaderSize;
    if (colsEnd < width) {
      gc.setBackground(theme.getHeaderShadowColor());
      gc.fillRectangle(colsEnd, 0, width - colsEnd, height);
    }
    int rowsEnd = rows.getTotal() - getYOffset() + columnHeaderSize;
    if (rowsEnd < height) {
      gc.setBackground(theme.getHeaderShadowColor());
      gc.fillRectangle(0, rowsEnd, width, height - rowsEnd);
    }
  }

  private void paintCells(PaintEvent e, Timer debug) {
    if (debug != null) {
      debug.subTask("Prepare offsets");
    }

    GC gc = e.gc;

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
    if (cleanRegion != null && debug != null) {
      System.out.printf("Clean region {%d, %d} - {%d, %d}\n", cleanRegion.x, cleanRegion.y,
          cleanRegion.x + cleanRegion.width, cleanRegion.y + cleanRegion.height);
    }
    for (int y = 0; row < rowCount && y + columnHeaderSize - yAdjust < height; ) {

      int col = firstCol;
      int tileHeight = 0;
      for (int x = 0; col < colCount && x + rowHeaderSize - xAdjust < width; ) {
        try {
          Point position = new Point(col, row);
          if (debug != null) {
            debug.subTask("Fetch cached tiles");
          }
          Image tile = tileCache.get(position, () -> {
            if (debug != null) {
              debug.subTask("Render tiles");
            }
            return renderTile(device, position);
          });
          if (tileHeight == 0) {
            tileHeight = (int) (tile.getBounds().height / deviceZoom);
          }

          if (tile != nullTile) {
            if (debug != null) {
              debug.subTask("Paint tiles");
            }
            int tileWidth = (int) (tile.getBounds().width / deviceZoom);
            int destX = x + rowHeaderSize - xAdjust;
            int destY = y + columnHeaderSize - yAdjust;
//            if (cleanRegion != null &&
//                cleanRegion.contains(destX, destY) &&
//                cleanRegion.contains(destX + tileWidth, destY + tileHeight)) {
////              System.out.printf("Skip tile {%d, %d}\n", x, y);
//            } else {
//              System.out.printf("Paint tile {%d, %d} at {%d, %d} - {%d, %d}\n", x, y,
//                  destX, destY, destX + tileWidth, destY + tileHeight);
              gc.drawImage(tile,
                  0, 0,
                  tile.getBounds().width, tile.getBounds().height,
                  destX, destY,
                  tileWidth, tileHeight);
//            }
            x += tileWidth;
          }
          col++;
        } catch (ExecutionException err) {
          err.printStackTrace();
        }
      }

      y += tileHeight;
      row += TILE_ROWS;
    }
    cleanRegion = null;
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
      if (text.isEmpty()) {
        return;
      }

      if (selection.isSelected(col, row)) {
        gc.setForeground(theme.getSelectionTextColor());
      } else {
        gc.setForeground(theme.getDefaultTextColor());
      }
      gc.drawString(text, getHorizontalCellPadding(), y + getVerticalCellPadding(), true);

    } catch (ExecutionException e) {
      e.printStackTrace();
    }
  }

  @Nonnull
  private Image renderTile(Device device, Point position) {
    try {
      int width = cols.getSize(position.x);

      int lastY = rows.getTotal();
      if (position.y + TILE_ROWS < rows.getCount()) {
        lastY = rows.getPosition(position.y + TILE_ROWS);
      }
      int height = lastY - rows.getPosition(position.y);

      if (width == 0 || height == 0) {
        return nullTile;
      }

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
      return nullTile;
    }
  }

  private void renderCurrentCell(GC gc, int y, Rectangle viewport) {
    if (currentCell.x == viewport.x && currentCell.y == viewport.y) {
      gc.setForeground(theme.getCurrentCellBorderColor());
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
    if (!selection.isSelected(viewport.x, viewport.y)) {
      return;
    }

    gc.setBackground(theme.getSelectionBackgroundColor());
    gc.fillRectangle(0, y, viewport.width, viewport.height);
  }

  private void paintCorner(PaintEvent e) {
    GC gc = e.gc;
    gc.setBackground(theme.getHeaderBackgroundColor());
    gc.setForeground(theme.getHeaderShadowColor());

    int rowHeaderSize = getRowHeaderSize();
    int columnHeaderSize = getColumnHeaderSize();

    gc.fillRectangle(0, 0, rowHeaderSize, columnHeaderSize);
    gc.drawLine(0, columnHeaderSize - 1, rowHeaderSize - 1, columnHeaderSize - 1);
    gc.drawLine(rowHeaderSize - 1, 0, rowHeaderSize - 1, columnHeaderSize - 1);
  }

  private void paintRowHeaders(PaintEvent e) {
    GC gc = e.gc;
    Rectangle bounds = canvas.getBounds();

    int columnHeaderSize = getColumnHeaderSize();
    int rowHeaderSize = getRowHeaderSize();
    int horizontalCellPadding = getHorizontalCellPadding();
    int verticalCellPadding = getVerticalCellPadding();

    Color backgroundColor = theme.getHeaderBackgroundColor();
    Color textColor = theme.getHeaderTextColor();
    Color shadowColor = theme.getHeaderShadowColor();
    Color highlightColor = theme.getHeaderHighlightColor();

    gc.setBackground(backgroundColor);
    gc.fillRectangle(0, columnHeaderSize, rowHeaderSize,
        canvas.getBounds().height - columnHeaderSize);

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

      if (y > bounds.height) {
        break;
      }
      if (y + height < bounds.y) {
        continue;
      }

      // Shadow header if row contains current cell
      if (currentCell != null && currentCell.y == i) {
        gc.setBackground(shadowColor);
        gc.setForeground(highlightColor);

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
      gc.drawString(text, rowHeaderSize - horizontalCellPadding - extent.x,
          y + verticalCellPadding);

      // Draw cell border
      gc.setForeground(shadowColor);
      gc.drawLine(0, y - 1, rowHeaderSize - 1, y - 1);
      gc.setForeground(highlightColor);
      gc.drawLine(0, y, rowHeaderSize - 1, y);

      y += height;
    }

    // Right of row headers
    gc.setForeground(shadowColor);
    gc.drawLine(rowHeaderSize - 1, columnHeaderSize, rowHeaderSize - 1, bounds.height);

    // Last row
    int bottomEdge = rows.getTotal() - yOffset + columnHeaderSize;
    if (bottomEdge < bounds.height) {
      gc.drawLine(0, bottomEdge, rowHeaderSize, bottomEdge);
    }

    gc.setClipping((Rectangle) null);
  }

  public int getYOffset() {
    return origin.y;
  }

  public void setYOffset(int yOffset) {
    canvas.getVerticalBar().setSelection(yOffset / SCROLL_FACTOR);
    getDisplay().asyncExec(this::scrollCanvas);
  }

  private void paintColHeaders(PaintEvent e) {
    GC gc = e.gc;
    Rectangle bounds = canvas.getBounds();

    int columnHeaderSize = getColumnHeaderSize();
    int rowHeaderSize = getRowHeaderSize();
    int horizontalCellPadding = getHorizontalCellPadding();
    int verticalCellPadding = getVerticalCellPadding();

    Color backgroundColor = theme.getHeaderBackgroundColor();
    Color textColor = theme.getHeaderTextColor();
    Color shadowColor = theme.getHeaderShadowColor();
    Color highlightColor = theme.getHeaderHighlightColor();

    gc.setBackground(backgroundColor);
    gc.fillRectangle(rowHeaderSize, 0, canvas.getBounds().width - rowHeaderSize, columnHeaderSize);

    int xOffset = getXOffset();

    int startCol = xOffset < cols.getTotal() ? cols.getItemAt(xOffset) : cols.getCount();
    for (int i = startCol; i < cols.getCount(); i++) {
      int width = cols.getSize(i);
      int x = rowHeaderSize + cols.getPosition(i) - xOffset;

      if (x > bounds.width) {
        break;
      }
      if (x + width < bounds.x) {
        continue;
      }

      // Shadow selected cell's column header
      if (currentCell != null && currentCell.x == i) {
        gc.setBackground(shadowColor);
        gc.setForeground(highlightColor);

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
      gc.setForeground(shadowColor);
      gc.drawLine(x - 1, 0, x - 1, columnHeaderSize - 1);
      gc.setForeground(highlightColor);
      gc.drawLine(x, 0, x, columnHeaderSize - 1);
    }

    // Bottom of header
    gc.setForeground(shadowColor);
    gc.drawLine(rowHeaderSize, columnHeaderSize - 1, bounds.width, columnHeaderSize - 1);

    // Last column
    int rightEdge = cols.getTotal() - xOffset + rowHeaderSize;
    if (rightEdge < bounds.width) {
      gc.drawLine(rightEdge, 0, rightEdge, columnHeaderSize);
    }
  }

  public int getXOffset() {
    return origin.x;
  }

  public void setXOffset(int xOffset) {
    canvas.getHorizontalBar().setSelection(xOffset / SCROLL_FACTOR);
    getDisplay().asyncExec(this::scrollCanvas);
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
    gc.setForeground(theme.getCellBorderColor());
    gc.setLineDash(new int[]{2, 2});

    gc.drawLine(0, y + viewport.height - 1, viewport.width - 1, y + viewport.height - 1);
    gc.drawLine(viewport.width - 1, y, viewport.width - 1, y + viewport.height - 1);

    gc.setLineStyle(SWT.LINE_SOLID);
  }

  private void renderBackground(GC gc, Rectangle viewport) {
    gc.setBackground(theme.getCellBackgroundColor());
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

  public void checkMaxScroll() {
    int maxOffset = getTotalHeight();
    if (getYOffset() > maxOffset) {
      setYOffset(maxOffset);
    }
  }

  private void updateVerticalScroll() {
    if (isDisposed()) {
      return;
    }

    int max = getTotalHeight() - canvas.getBounds().height;
    ScrollBar bar = canvas.getVerticalBar();
    if (max > 0) {
      bar.setVisible(true);
      int total =
          (max + canvas.getHorizontalBar().getSize().y + getColumnHeaderSize()) / SCROLL_FACTOR
              + 11;
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

    int max = getTotalWidth() + getRowHeaderSize() - canvas.getBounds().width ;
    ScrollBar bar = canvas.getHorizontalBar();
    if (max > 0 || bar.getSelection() > 0) {
      bar.setVisible(true);
      int total =
          (max + canvas.getVerticalBar().getSize().x ) / SCROLL_FACTOR + 11;
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
      rowHeaderSize = (int) (gc.getFontMetrics().getAverageCharacterWidth() * 10
          + 2 * getHorizontalCellPadding());
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
    if (!canvas.isDisposed()) {
      canvas.redraw();
    }
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
//    refresh();
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
//    updateVerticalScroll();
//
//    refresh();
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

    SizeTree.SizeInfo newCol = cols.getSizeInfo(cell.x);
    SizeTree.SizeInfo newRow = rows.getSizeInfo(cell.y);
    int x = newCol.getPos();
    int y = newRow.getPos();
    int width = newCol.getSize();
    int height = newRow.getSize();

    int columnHeaderSize = getColumnHeaderSize();
    int rowHeaderSize = getRowHeaderSize();

    int xOffset = getXOffset();
    int yOffset = getYOffset();

    invalidateTiles(new Rectangle(cell.x, cell.y, 1, 1));
    if (currentCell != null) {
      invalidateTiles(new Rectangle(currentCell.x, currentCell.y, 1, 1));
      if (cell.x != currentCell.x && currentCell.x < cols.getCount()) {
        SizeTree.SizeInfo oldCol = cols.getSizeInfo(currentCell.x);
        canvas.redraw(oldCol.getPos() - xOffset + rowHeaderSize, 0, oldCol.getSize(),
            columnHeaderSize, false);
        canvas.redraw(x - xOffset + rowHeaderSize, 0, width, columnHeaderSize, false);
      }
      if (cell.y != currentCell.y && currentCell.y < rows.getCount()) {
        SizeTree.SizeInfo oldRow = rows.getSizeInfo(currentCell.y);
        canvas.redraw(0, oldRow.getPos() - yOffset + columnHeaderSize, rowHeaderSize,
            oldRow.getSize(), false);
        canvas.redraw(0, y - yOffset + columnHeaderSize, rowHeaderSize, height, false);
      }
    }
    currentCell = cell;

    currentCellListeners.forEach(l -> l.notify(currentCell));

    // Adjust scroll position to ensure current cell is visible
    if (x + width / 2 < xOffset) {
      setXOffset(x);
    } else {
      int canvasWidth = canvas.getBounds().width;
      if (x + 16 > xOffset + canvasWidth - rowHeaderSize - 16) {
        setXOffset(x - canvasWidth + rowHeaderSize + width + 32);
      }
    }

    if (y < yOffset) {
      setYOffset(y);
    } else {
      int canvasHeight = canvas.getBounds().height;
      if (y + height > yOffset + canvasHeight - columnHeaderSize - 16) {
        setYOffset(y - canvasHeight + columnHeaderSize + height + 32);
      }
    }
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
        if (r.intersects(tileRegion)) {
          return true;
        }
      }
      return false;
    });

    Rectangle repaintRegion = null;
    for (Rectangle region : regions) {
      if (repaintRegion == null) {
        repaintRegion = region;
      } else {
        repaintRegion = repaintRegion.union(region);
      }
    }
    if (repaintRegion != null) {
      // If the size of the cell grid has changed due to changing the delimiter, the repaint region will need adjusting
      repaintRegion = repaintRegion.intersection(
          new Rectangle(0, 0, cols.getCount(), rows.getCount()));
    }
    if (repaintRegion != null && repaintRegion.width > 0 && repaintRegion.height > 0) {
      int firstCol = cols.getPosition(repaintRegion.x);
      SizeTree.SizeInfo lastCol = cols.getSizeInfo(repaintRegion.x + repaintRegion.width - 1);

      int firstRow = rows.getPosition(repaintRegion.y);
      SizeTree.SizeInfo lastRow = rows.getSizeInfo(repaintRegion.y + repaintRegion.height - 1);

      int x = firstCol - origin.x + rowHeaderSize - 1;
      int y = firstRow - origin.y + columnHeaderSize - 1;
      int width = lastCol.getPos() + lastCol.getSize() - firstCol + 2;
      int height = lastRow.getPos() + lastRow.getSize() - firstRow + 2;

      canvas.redraw(x, y, width, height, false);
    }
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
          if (label.isEmpty()) {
            continue;
          }

          Integer size = colSizes.get(col);
          if (size == null) {
            size = 0;
          }
          colSizes.put(col, Math.max(size, label.length()));
        }
      }
    }

    StringBuilder text = new StringBuilder();
    StringBuilder html = new StringBuilder(
        "<table style=\"border: 1px solid #999;\" cellspacing=\"0\">" + nl);

    for (int row = 0; row < bounds.height; row++) {
      html.append("<tr>" + nl);
      for (int col = 0; col < bounds.width; col++) {
        Integer size = colSizes.get(col);
        if (size == null || size == 0) {
          continue;
        }

        String label = "";
        if (selection.isSelected(col + bounds.x, row + bounds.y)) {
          label = getLabel(col + bounds.x, row + bounds.y);
        }

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

    Object[] data = new Object[]{text.toString(), html.toString()};

    TextTransfer textTransfer = TextTransfer.getInstance();
    HTMLTransfer htmlTransfer = HTMLTransfer.getInstance();
    Transfer[] transfers = new Transfer[]{textTransfer, htmlTransfer};

    Clipboard clipboard = new Clipboard(getDisplay());
    clipboard.setContents(data, transfers);
    clipboard.dispose();
  }

  public void addCurrentCellListener(CellListener listener) {
    currentCellListeners.add(listener);
  }

  public void setTheme(Theme theme) {
    this.theme = theme;
    redrawTiles();
  }
}
